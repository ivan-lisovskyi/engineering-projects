//Copyright (c) 2013-2025, The MercuryDPM Developers Team. All rights reserved.
//For the list of developers, see <http://www.MercuryDPM.org/Team>.
//
//Redistribution and use in source and binary forms, with or without
//modification, are permitted provided that the following conditions are met:
//  * Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
//  * Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
//  * Neither the name MercuryDPM nor the
//    names of its contributors may be used to endorse or promote products
//    derived from this software without specific prior written permission.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
//ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE MERCURYDPM DEVELOPERS TEAM BE LIABLE FOR ANY
//DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
//(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
//LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
//ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Periodic clay platelet driver using MercuryDPM with PVFMM long-range forces.

#include <../pvfmm/include/pvfmm.hpp>
#include <../pvfmm/examples/include/utils.hpp>
#include <iostream>
#include "Species/LinearViscoelasticFrictionSpecies.h"
#include <Mercury3D.h>
#include "../../Clump/ClumpHeaders/ClumpInput.h"
#include "../../Clump/ClumpHeaders/Mercury3DClump.h"
#include "Boundaries/PeriodicBoundary.h"
#include <CMakeDefinitions.h>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

typedef std::vector<double> dvec; // General-purpose double vectors
MPI_Comm comm = MPI_COMM_WORLD; // Need to have MPI communicator global (as it is accessed from the scope of actionsAfterTimeStep)

// Simulation constants

const int DIM = 3;                      // Space dimension
const int MULT_ORDER = 6;               // Order of algebraic multipole expansion for PVFMM
const int TARGET_OUTPUT_FRAMES = 500;   // Requested number of saved VTU/VTP intervals.
const int LR_STRIDE = 10;              // Recompute long-range forces every LR_STRIDE time steps.
const int EIGEN_POWER_ITERATIONS = 80; // Small fixed iteration count for the 3x3 orientation tensor.
const double TOL = 10e-10;              // Keeps PVFMM coordinates inside the open unit cube.
const int CLUMP_INDEX = 1;              // Number of the clump instance used in this simulation
const bool PERIODIC = true;            // true: 3D periodic cell; false: elastic box
const int PVFMM_MEM_MNGR = 10000000;    // PVFMM memory-pool size
const int PVFMM_MAX_PTS = 600;          // Maximum number of points per leaf in PVFMM
const bool EXCLUDE_OVERLAPS = false;    // Deposit clumps without overlaps (N^2-hard, may be slow)

// Problem parameters

double f_min = 0; double f_max = 1;             // Box size; KIFMM expects the unit cube.
double av_min = -0;                             // Range of angular velocities
double av_max =  0;
double tv_min = -0;                             // Range of translational velocities
double tv_max =  0;
int N_att = 50;                               // Clump insertion attempts; accepted count can be lower.
double clumpMargin = 0.0;                       // Initial placement margin from the box boundaries.
double boxMargin = 0.05;                         // Elastic box margin
double LRMagnitude = 0.01;                   // Scales the q_i q_j/(4*pi*r^2) long-range force.
double clumpDamping = 10e-4;                    // Extra viscous damping added to rotational and translational DOFs of clumps
double clumpScale = 0.1;                       // Size - S^1, mass, volume S^3, TOI: S^5
double specDens = 1;                            // (Mass) density of the particle species
double specDiss = 0.01;                         // Species dissipation
double specStif = 1e6;                          // Stiff enough to avoid near-singular long-range contacts.
double timeStep = 1.0e-06;                      // Time integration timestep
double timeMax = 0.2;                            // Simulation duration
double solutionPH = 8.0;                         // Reduced pH-dependent top/bottom/edge charge model
unsigned int randomSeed = 1;                     // Seed for ClumpInput::RandomDouble(), which uses C rand().
int outputStride = 1;                             // Computed after timeMax/timeStep are known.
Vec3D  gravity = Vec3D(0.0, 0.0, 0.0);  // Constant external force field (should not be used with periodic BCs)
dvec PELog = {};                                // Potential-energy history for the long-range part.

// Per-platelet orientation
struct PlateletOrientation
{
    Vec3D centre;
    Vec3D normal;
    double localSz = 0.0;
};

// One global fabric/orientation measurement at a saved time step.
struct AnisotropySnapshot
{
    int nClumps = 0;
    int validClumps = 0;
    double Sz = 0.0;
    double lambdaMax = 0.0;
    double S_tensor = 0.0;
    double Axx = 0.0;
    double Axy = 0.0;
    double Axz = 0.0;
    double Ayy = 0.0;
    double Ayz = 0.0;
    double Azz = 0.0;
    std::vector<PlateletOrientation> platelets;
};

double largestEigenvalueSymmetric3x3(const double Axx, const double Axy, const double Axz,
                                     const double Ayy, const double Ayz, const double Azz)
{
    // Power iteration is enough here: the matrix is only 3x3 and symmetric.
    const double invSqrt3 = 1.0 / std::sqrt(3.0);
    double vx = invSqrt3;
    double vy = invSqrt3;
    double vz = invSqrt3;

    for (int iter = 0; iter < EIGEN_POWER_ITERATIONS; ++iter)
    {
        const double wx = Axx * vx + Axy * vy + Axz * vz;
        const double wy = Axy * vx + Ayy * vy + Ayz * vz;
        const double wz = Axz * vx + Ayz * vy + Azz * vz;
        const double norm = std::sqrt(wx * wx + wy * wy + wz * wz);
        if (norm < 1.0e-15) return 0.0;
        vx = wx / norm;
        vy = wy / norm;
        vz = wz / norm;
    }

    return Axx * vx * vx + Ayy * vy * vy + Azz * vz * vz
         + 2.0 * (Axy * vx * vy + Axz * vx * vz + Ayz * vy * vz);
}

// Brute-force pair force computation for validation only; use with PERIODIC = false.
void computeLRForcesBF(dvec& positions, dvec& forces, dvec &densities, MPI_Comm comm) {
    forces.resize(positions.size());
    int DIM = 3;
    int N = positions.size() / DIM;
    for (int i = 0; i < N; i++) { // Loop over targets
        forces[i * DIM + 0] = 0;
        forces[i * DIM + 1] = 0;
        forces[i * DIM + 2] = 0;
        for (int j = 0; j < N; j++) { // Loop over sources
            if (i != j) {
                double rad2 = (positions[DIM * i + 0] - positions[DIM * j + 0]) *
                              (positions[DIM * i + 0] - positions[DIM * j + 0]) +
                              (positions[DIM * i + 1] - positions[DIM * j + 1]) *
                              (positions[DIM * i + 1] - positions[DIM * j + 1]) +
                              (positions[DIM * i + 2] - positions[DIM * j + 2]) *
                              (positions[DIM * i + 2] - positions[DIM * j + 2]);

                double force_ij = densities[j] / (4 * M_PI * std::sqrt(rad2));
                dvec normal_ij = {(positions[DIM * i + 0] - positions[DIM * j + 0]) / rad2,
                                 (positions[DIM * i + 1] - positions[DIM * j + 1]) / rad2,
                                 (positions[DIM * i + 2] - positions[DIM * j + 2]) / rad2};

                forces[i * DIM + 0] += force_ij * normal_ij[0];
                forces[i * DIM + 1] += force_ij * normal_ij[1];
                forces[i * DIM + 2] += force_ij * normal_ij[2];
            }
        }
    }
}

// KIFMM pair force computation
void computeLRForces(dvec& positions, dvec& forces, dvec &densities, MPI_Comm comm){
    const pvfmm::Kernel<double>& kernel_fn=pvfmm::LaplaceKernel<double>::gradient();

    dvec  dl_coord = {};                            // coordinates of sources (double layer)
    dvec dl_den = {};                               // Density of double layer potential

    size_t n_sl = positions.size()/DIM;             // number of sources (single layer)
    size_t n_dl = 0;                                // number of sources (double layer)
    size_t n_trg = positions.size()/DIM;            // number of targets

    // Create memory-manager (optional)
    pvfmm::mem::MemoryManager * mem_mgr = new pvfmm::mem::MemoryManager(PVFMM_MEM_MNGR);

    // Construct tree

    pvfmm::BoundaryType BT;
    if (PERIODIC){BT = pvfmm::Periodic;}
    else        {BT = pvfmm::FreeSpace;}
    auto* tree=pvfmm::PtFMM_CreateTree(positions, densities, {}, {}, positions, comm, PVFMM_MAX_PTS, BT);

    // Load matrices.
    pvfmm::PtFMM<double> * matrices = new pvfmm::PtFMM<double>(mem_mgr);
    matrices->Initialize(MULT_ORDER, comm, &kernel_fn);

    // FMM Setup
    tree->SetupFMM(matrices);

    // Run FMM
    pvfmm::PtFMM_Evaluate(tree, forces, n_trg);

    delete tree;
    delete matrices;
    delete mem_mgr;
}

// Compute the long-range potential energy with the same boundary condition as the force solve.
double computeLRPotEnergy(dvec& positions, dvec &densities, MPI_Comm comm){
    const pvfmm::Kernel<double>& kernel_fn=pvfmm::LaplaceKernel<double>::potential();

    dvec  dl_coord = {};                            // coordinates of sources (double layer)
    dvec dl_den = {};                               // Density of double layer potential

    size_t n_sl = positions.size()/DIM;             // number of sources (single layer)
    size_t n_dl = 0;                                // number of sources (double layer)
    size_t n_trg = positions.size()/DIM;            // number of targets
    dvec potentials(n_trg);
    // Create memory-manager (optional)
    pvfmm::mem::MemoryManager * mem_mgr = new pvfmm::mem::MemoryManager(PVFMM_MEM_MNGR);

    // Use the same boundary model as computeLRForces; otherwise U_lr is not
    // the potential energy corresponding to the forces that move the clumps.
    pvfmm::BoundaryType BT;
    if (PERIODIC){BT = pvfmm::Periodic;}
    else        {BT = pvfmm::FreeSpace;}
    auto* tree=pvfmm::PtFMM_CreateTree(positions, densities, {}, {}, positions, comm, PVFMM_MAX_PTS, BT);

    // Load matrices
    pvfmm::PtFMM<double> * matrices = new pvfmm::PtFMM<double>(mem_mgr);
    matrices->Initialize(MULT_ORDER, comm, &kernel_fn);

    // FMM Setup
    tree->SetupFMM(matrices);

    // Run FMM
    pvfmm::PtFMM_Evaluate(tree, potentials, n_trg);

    // Normalize forces by target densities - PVFMM and BF routines do not do that by default
    for (int i = 0; i< densities.size(); i++) {potentials[i] *= densities[i];}

    double potEnergy = 0.0;
    for (double x : potentials) potEnergy += x;
    potEnergy /=2.0;

    delete tree;
    delete matrices;
    delete mem_mgr;

    return potEnergy;
}


// Build one hexagonal platelet layer around the given centre.
std::vector<dvec> generate_hexagonal_lattice(const dvec& center, double spacing, int N)
{
    std::vector<dvec> points;
    if (N < 1 || center.size() != 3) return points;

    double a1x = spacing;
    double a1y = 0.0;
    double a2x = spacing * 0.5;
    double a2y = spacing * std::sqrt(3.0) / 2.0;

    int radius = N - 1; // Hex radius in lattice steps.

    for (int i = -radius; i <= radius; ++i)
    {
        for (int j = -radius; j <= radius; ++j)
        {
            if (std::abs(i + j) <= radius)
            {
                double x = center[0] + i * a1x + j * a2x;
                double y = center[1] + i * a1y + j * a2y;
                double z = center[2];
                points.push_back({x, y, z});
            }
        }
    }

    return points;
}

// A site is on the boundary if it lies on any side of the hexagonal ring.
bool is_hex_boundary_site(int i, int j, int radius)
{
    return std::abs(i) == radius || std::abs(j) == radius || std::abs(i + j) == radius;
}

// Append surface labels in the same order as generate_hexagonal_lattice().
void append_surface_labels_for_hex_layer(int N, int faceType, std::vector<int>& surfaceType)
{
    if (N < 1) return;

    int radius = N - 1;

    for (int i = -radius; i <= radius; ++i)
    {
        for (int j = -radius; j <= radius; ++j)
        {
            if (std::abs(i + j) <= radius)
            {
                // Treat the outer ring as the edge; interior sites keep the face label.
                int s = is_hex_boundary_site(i, j, radius) ? 0 : faceType;
                surfaceType.push_back(s);
            }
        }
    }
}


// Sum the inertia tensor of all pebbles about the clump centre.
std::vector<double> total_inertia_tensor(const std::vector<dvec>& coords, double specDens, double radius)
{
    // Tensor components in row-major order: {I11, I12, I13, I21, I22, I23, I31, I32, I33, MASS}
    std::vector<double> I(10, 0.0);
    if (coords.empty()) return I;
    double mass = (4./3.) * M_PI * radius * radius * radius * specDens;
    I[9] = mass * coords.size();
    std::cout<<"mass of the clump: " << I[9] <<std::endl;
    double I_self = (2.0 / 5.0) * mass * radius * radius;

    for (const auto& r : coords)
    {
        if (r.size() != 3) continue;
        double x = r[0], y = r[1], z = r[2];
        double r2 = x * x + y * y + z * z;

        // Diagonal terms
        I[0] += I_self + mass * (r2 - x * x); // I11
        I[4] += I_self + mass * (r2 - y * y); // I22
        I[8] += I_self + mass * (r2 - z * z); // I33

        // Off-diagonal terms (note the negative sign)
        double mxy = -mass * x * y;
        double mxz = -mass * x * z;
        double myz = -mass * y * z;

        I[1] += mxy; I[3] += mxy; // I12 = I21
        I[2] += mxz; I[6] += mxz; // I13 = I31
        I[5] += myz; I[7] += myz; // I23 = I32
    }

    return I;
}

int computeOutputStride(const double duration, const double dt, const int targetFrames)
{
    if (!(duration > 0.0) || !(dt > 0.0) || targetFrames <= 0) return 1;
    return std::max(1, static_cast<int>(std::llround(duration / (dt * targetFrames))));
}


class Clay : public Mercury3Dclump
{
public:

    dvec forces_;
    dvec densities_;
    int timestep_;
    int N_part_;

    // Geometry and surface labels for the custom platelet clump.
    std::vector <double> II;
    std::vector <dvec> cl_coords;
    std::vector <int> cl_surface; // +1 top, -1 bottom, 0 edge
    double pebble_rad;
    double spacing;
    int N_side;


    explicit  Clay()
    {
        timestep_ = 0;
        N_part_ = 0;
        setGravity(gravity);
        setName("clay_anisotropy_updated");
        setXBallsAdditionalArguments("-solidf -v0");
        setXMax(f_max);
        setYMax(f_max);
        setZMax(f_max); // Domain upper Z bound
        setXMin(f_min);
        setYMin(f_min);
        setZMin(f_min);

        setCustomClump();

    }

    void setClumpDamping(Mdouble damp){ clump_damping = damp;}

    void setClumpIndex(Mdouble index){ clump_index = index;}

    void setClumpMass(Mdouble mass){clump_mass = mass;}
    Mdouble getClumpMass(){return clump_mass;}

    void setCustomClump(){
        // Build the two-layer platelet geometry and cache its inertia.
        double CS1 = clumpScale; double CS3 = CS1*CS1*CS1; double CS5 = CS3*CS1*CS1; // Clump scale powers

        cl_coords.clear();
        cl_surface.clear();

        dvec center = {0., 0., 0.}; spacing = 0.1*CS1; N_side = 14; pebble_rad = 0.08*CS1;
        dvec center_l1 = {center[0], center[1], center[2] + 0.2 * spacing};
        dvec center_l3 = {center[0], center[1], center[2] - 0.2 * spacing};

        std::vector<dvec> l1 = generate_hexagonal_lattice(center_l1, spacing, N_side);
        std::vector<dvec> l3 = generate_hexagonal_lattice(center_l3, spacing, N_side);

        cl_coords.insert(cl_coords.end(), l1.begin(),l1.end());
        cl_coords.insert(cl_coords.end(), l3.begin(),l3.end());

        append_surface_labels_for_hex_layer(N_side, +1, cl_surface);
        append_surface_labels_for_hex_layer(N_side, -1, cl_surface);

        II = total_inertia_tensor(cl_coords, specDens, pebble_rad);
        setClumpMass(II[9]);
    }

    void setupInitialConditions() override {

        setParticlesWriteVTK(1);

        if (PERIODIC) {
            // Periodic box

            auto per_x = boundaryHandler.copyAndAddObject(new PeriodicBoundary);
            per_x->set(Vec3D(1, 0, 0), getXMin(), getXMax());

            auto per_y = boundaryHandler.copyAndAddObject(new PeriodicBoundary);
            per_y->set(Vec3D(0, 1, 0), getYMin(), getYMax());

            auto per_z = boundaryHandler.copyAndAddObject(new PeriodicBoundary);
            per_z->set(Vec3D(0, 0, 1), getZMin(), getZMax());
        }
        else
        {
            // Elastic box

            wallHandler.clear();
            InfiniteWall w0;
            w0.setSpecies(speciesHandler.getObject(0));
            w0.set(Vec3D(-1.0, 0.0, 0.0), Vec3D(getXMin() + boxMargin, 0, 0));
            wallHandler.copyAndAddObject(w0);
            w0.set(Vec3D(1.0, 0.0, 0.0), Vec3D(getXMax() - boxMargin, 0, 0));
            wallHandler.copyAndAddObject(w0);
            w0.set(Vec3D(0.0, -1.0, 0.0), Vec3D(0, getYMin() + boxMargin, 0));
            wallHandler.copyAndAddObject(w0);
            w0.set(Vec3D(0.0, 1.0, 0.0), Vec3D(0, getYMax() - boxMargin, 0));
            wallHandler.copyAndAddObject(w0);
            w0.set(Vec3D(0.0, 0.0, -1.0), Vec3D(0, 0, getZMin() + boxMargin ));
            wallHandler.copyAndAddObject(w0);
            w0.set(Vec3D(0.0, 0.0, 1.0), Vec3D(0, 0, getZMax() - boxMargin));
            wallHandler.copyAndAddObject(w0);

        }




        // Generate the initial clump population.
        //setClumpIndex(CLUMP_INDEX);
        // double CS1 = clumpScale; double CS3 = CS1*CS1*CS1; double CS5 = CS3*CS1*CS1; // Clump scale powers



        //std::cout<<"cl_coords.size() = "<<cl_coords.size()<<std::endl;


        double sign  = 1;

        std::cout<<"Start clump generation procedure..."<<std::endl;
        for (int part = 0; part<N_att; part++) {

            dvec clump_den_raw; // Prescribed reduced top/bottom/edge source values.
            dvec clump_den_solver; // zero-mean copy used by the periodic long-range solver
            ClumpParticle p0;
            p0.setSpecies(speciesHandler.getObject(0)); // Material model for this clump.
            p0.setClump(); // Mark p0 as a rigid clump.
            p0.setRadius(pebble_rad); // Clump radius is used for visualization only
            p0.setCharge(0.0); // Pebble charges define the long-range sources; the clump centre should not add charge.


            for (int j = 0; j < cl_coords.size(); j++) {
                // First build the raw pH-dependent charge template.
                // The template describes the intended top/bottom/edge chemistry
                // before the numerical neutrality correction required by the
                // periodic KIFMM solve.
                double ph = solutionPH;
                if (ph < 3.0) ph = 3.0;
                if (ph > 10.0) ph = 10.0;

                double lambda3 = (10.0 - ph) / 7.0;
                double lambda10 = (ph - 3.0) / 7.0;

                Mdouble den = 0;
                if (cl_surface[j] == +1)
                {den = -0.22;}
                else if (cl_surface[j] == -1)
                {den = lambda3 * (+0.20) + lambda10 * (-0.20);}
                else
                {den = lambda3 * (+0.06) + lambda10 * (-0.06);}

                clump_den_raw.push_back(den);
            }

            clump_den_solver = clump_den_raw;
            if (PERIODIC && !clump_den_solver.empty()) {
                // A periodic electrostatic solve cannot represent a net charge
                // without an implicit neutralizing background. We therefore use a
                // zero-mean version of the raw top/bottom/edge template for both
                // the pebble charges and the FMM source densities. This keeps
                // ParaView's Charge field consistent with the forces that KIFMM
                // actually computes.
                //
                // Important physical limitation: at high pH the raw reduced
                // kaolinite template can be net negative. Subtracting the mean
                // charge is a numerical periodic-boundary correction; it may shift
                // some sites toward more positive values. Interpret the corrected
                // values as the periodic, neutralized charge distribution, not as
                // a full surface-complexation prediction.
                double averageCharge = 0.0;
                for (auto den : clump_den_solver) averageCharge += den;
                averageCharge /= clump_den_solver.size();
                for (auto& den : clump_den_solver) den -= averageCharge;
            }

            for (int j = 0; j < cl_coords.size(); j++) {
                // Use the same final charge everywhere: on the pebble itself
                // for output/contact data, and later in densities_ for KIFMM.
                const Mdouble den = clump_den_solver[j];
                p0.addPebble(Vec3D(cl_coords[j][0],
                                   cl_coords[j][1],
                                   cl_coords[j][2]),
                             pebble_rad, den);
            }

            // Apply the precomputed rigid-body properties.
            p0.setPrincipalDirections(
                    Matrix3D(1., 0., 0.,
                             0., 1., 0.,
                             0., 0., 1.));
            p0.setInitInertia(MatrixSymmetric3D(II[0], II[1], II[2],
                                                    II[4], II[5],
                                                    II[8]));
            p0.setClumpMass(II[9]);

            p0.setDamping(clump_damping);

            // Clump initial position, velocity and angular velocity
            Vec3D pos = Vec3D(f_min + clumpMargin + RandomDouble(f_max - f_min - 2 * clumpMargin),
                              f_min + clumpMargin + RandomDouble(f_max - f_min - 2 * clumpMargin),
                              f_min + clumpMargin + part * 0.019);

            //pos = Vec3D(0.5, 0.5, 0.5);

            Vec3D angVel = Vec3D(av_min + RandomDouble(av_max - av_min),
                                 av_min + RandomDouble(av_max - av_min),
                                 av_min + RandomDouble(av_max - av_min));

            Vec3D vel = Vec3D(tv_min + RandomDouble(tv_max - tv_min),
                              tv_min + RandomDouble(tv_max - tv_min),
                              tv_min + RandomDouble(tv_max - tv_min));

            p0.setPosition(pos);
            p0.setVelocity(vel);
            p0.setAngularVelocity(angVel);

            // Keep the clump only if it passes the overlap check.
            if ((!EXCLUDE_OVERLAPS)||(checkClumpForInteractionPeriodic(p0))) {
                if (!(N_part_%100)) std::cout<<N_part_<<" ";
                particleHandler.copyAndAddObject(p0);
                for (auto n : clump_den_solver) densities_.push_back(n); // add the periodic-safe charge densities
                //sign = -sign;
                N_part_++;
            }
        }

        std::cout<<"Number of particles created: "<<N_part_<<std::endl;

        // Check that the periodic source distribution is neutral.
        double totCharge = 0;
        for (auto& n : densities_) totCharge += n;
        if ( PERIODIC && (std::fabs(totCharge) > TOL)) {
            std::cout<<"Periodic coundary conditions are used with nonzero net charge!"<<std::endl;
        }

        int mpiRank = 0;
        MPI_Comm_rank(comm, &mpiRank);
        if (mpiRank == 0) {
            std::ofstream fabricOut("AnisotropyLog.csv");
            fabricOut << "time,step,n_clumps,valid_clumps,S_z,S_tensor,lambda_max,"
                      << "Axx,Axy,Axz,Ayy,Ayz,Azz\n";
        }
    }

    AnisotropySnapshot computeAnisotropySnapshot()
    {
        AnisotropySnapshot snapshot;

        for (auto it = particleHandler.begin(); it != particleHandler.end(); ++it) {
            if (!(*it)->isClump()) continue;

            ++snapshot.nClumps;

            // The platelet normal is the third principal-direction axis of the
            // clump. This matches the actual rigid-clump geometry and also sees
            // the clump's own rotation during the simulation.
            ClumpParticle* clump = dynamic_cast<ClumpParticle*>(*it);
            if (clump == nullptr) continue;
            Vec3D normal = clump->getPrincipalDirections_e3();
            const double norm = normal.getLength();
            if (!(norm > 1.0e-15) || !std::isfinite(norm)) continue;
            normal = normal / norm;

            const double localSz = 0.5 * (3.0 * normal.Z * normal.Z - 1.0);
            snapshot.platelets.push_back({(*it)->getPosition(), normal, localSz});

            // Accumulate <n_i n_j>; the largest eigenvalue gives the scalar
            // nematic order parameter below.
            snapshot.Axx += normal.X * normal.X;
            snapshot.Axy += normal.X * normal.Y;
            snapshot.Axz += normal.X * normal.Z;
            snapshot.Ayy += normal.Y * normal.Y;
            snapshot.Ayz += normal.Y * normal.Z;
            snapshot.Azz += normal.Z * normal.Z;
            snapshot.Sz  += localSz;
            ++snapshot.validClumps;
        }

        if (snapshot.validClumps == 0) return snapshot;

        const double invN = 1.0 / static_cast<double>(snapshot.validClumps);
        snapshot.Axx *= invN;
        snapshot.Axy *= invN;
        snapshot.Axz *= invN;
        snapshot.Ayy *= invN;
        snapshot.Ayz *= invN;
        snapshot.Azz *= invN;
        snapshot.Sz *= invN;
        snapshot.lambdaMax = largestEigenvalueSymmetric3x3(snapshot.Axx, snapshot.Axy, snapshot.Axz,
                                                           snapshot.Ayy, snapshot.Ayz, snapshot.Azz);
        snapshot.S_tensor = 0.5 * (3.0 * snapshot.lambdaMax - 1.0);
        return snapshot;
    }

    void appendAnisotropyLog(const AnisotropySnapshot& snapshot)
    {
        int mpiRank = 0;
        MPI_Comm_rank(comm, &mpiRank);
        // Rank 0 owns diagnostic files so MPI runs do not duplicate rows.
        if (mpiRank != 0) return;

        std::ofstream fabricOut("AnisotropyLog.csv", std::ios::app);
        fabricOut << getTime() << ","
                  << timestep_ << ","
                  << snapshot.nClumps << ","
                  << snapshot.validClumps << ","
                  << snapshot.Sz << ","
                  << snapshot.S_tensor << ","
                  << snapshot.lambdaMax << ","
                  << snapshot.Axx << ","
                  << snapshot.Axy << ","
                  << snapshot.Axz << ","
                  << snapshot.Ayy << ","
                  << snapshot.Ayz << ","
                  << snapshot.Azz << "\n";
    }

    void writePlateletNormalsVtp(const AnisotropySnapshot& snapshot)
    {
        int mpiRank = 0;
        MPI_Comm_rank(comm, &mpiRank);
        if (mpiRank != 0) return;

        std::ostringstream name;
        name << "clay_anisotropy_updated_normals_"
             << std::setw(8) << std::setfill('0') << timestep_
             << ".vtp";

        std::ofstream out(name.str());
        const auto n = snapshot.platelets.size();

        // Point-only VTP: one point per clump, carrying its normal and local S_z.
        out << "<?xml version=\"1.0\"?>\n";
        out << "<VTKFile type=\"PolyData\" version=\"0.1\" byte_order=\"LittleEndian\">\n";
        out << "  <PolyData>\n";
        out << "    <Piece NumberOfPoints=\"" << n
            << "\" NumberOfVerts=\"" << n
            << "\" NumberOfLines=\"0\" NumberOfStrips=\"0\" NumberOfPolys=\"0\">\n";

        out << "      <Points>\n";
        out << "        <DataArray type=\"Float64\" NumberOfComponents=\"3\" format=\"ascii\">\n";
        for (const auto& p : snapshot.platelets) {
            out << "          " << p.centre.X << " " << p.centre.Y << " " << p.centre.Z << "\n";
        }
        out << "        </DataArray>\n";
        out << "      </Points>\n";

        out << "      <PointData Scalars=\"S_z_local\" Vectors=\"platelet_normal\">\n";
        out << "        <DataArray type=\"Float64\" Name=\"platelet_normal\" NumberOfComponents=\"3\" format=\"ascii\">\n";
        for (const auto& p : snapshot.platelets) {
            out << "          " << p.normal.X << " " << p.normal.Y << " " << p.normal.Z << "\n";
        }
        out << "        </DataArray>\n";
        out << "        <DataArray type=\"Float64\" Name=\"S_z_local\" format=\"ascii\">\n";
        for (const auto& p : snapshot.platelets) {
            out << "          " << p.localSz << "\n";
        }
        out << "        </DataArray>\n";
        out << "      </PointData>\n";

        out << "      <Verts>\n";
        out << "        <DataArray type=\"Int32\" Name=\"connectivity\" format=\"ascii\">\n";
        for (size_t i = 0; i < n; ++i) out << " " << i;
        out << "\n        </DataArray>\n";
        out << "        <DataArray type=\"Int32\" Name=\"offsets\" format=\"ascii\">\n";
        for (size_t i = 0; i < n; ++i) out << " " << (i + 1);
        out << "\n        </DataArray>\n";
        out << "      </Verts>\n";
        out << "    </Piece>\n";
        out << "  </PolyData>\n";
        out << "</VTKFile>\n";
    }

    void writePlateletNormalsPvd()
    {
        int mpiRank = 0;
        MPI_Comm_rank(comm, &mpiRank);
        if (mpiRank != 0) return;

        // Rebuild the collection file so ParaView can open the full time series.
        std::ofstream out("clay_anisotropy_updated_normals.pvd");
        out << "<?xml version=\"1.0\"?>\n";
        out << "<VTKFile type=\"Collection\" version=\"0.1\" byte_order=\"LittleEndian\">\n";
        out << "  <Collection>\n";

        for (int step = outputStride; step <= timestep_; step += outputStride) {
            std::ostringstream name;
            name << "clay_anisotropy_updated_normals_"
                 << std::setw(8) << std::setfill('0') << step
                 << ".vtp";
            out << "    <DataSet timestep=\"" << step * timeStep
                << "\" group=\"\" part=\"0\" file=\"" << name.str() << "\"/>\n";
        }

        out << "  </Collection>\n";
        out << "</VTKFile>\n";
    }


    void actionsAfterTimeStep() override {

        timestep_++;
        bool failed = false; // Tracks invalid positions or KIFMM output.

        // Keep positions and forces in particleHandler order so force[p] is
        // applied back to the same pebble.

        dvec positions;
        // Assemble the PVFMM target/source coordinates.
        for (std::vector<BaseParticle *>::iterator it = particleHandler.begin();
             it != particleHandler.end(); ++it) {
            if ((*it)->isPebble()) {
                Vec3D pos = (*it)->getPosition();

                if (std::isnan(pos[0]) || std::isnan(pos[1]) || std::isnan(pos[2])) std::cout<<"Bad position: "<<pos[0]<<" "<<pos[1]<<" "<<pos[2]<<std::endl;

                // Pebbles can leave the primary cell through their parent clump;
                // wrap them before passing coordinates to PVFMM.
                if (PERIODIC)
                {
                    if (pos.X>1.) pos.X = pos.X - 1.; if (pos.X<0.) pos.X = pos.X + 1.;
                    if (pos.Y>1.) pos.Y = pos.Y - 1.; if (pos.Y<0.) pos.Y = pos.Y + 1.;
                    if (pos.Z>1.) pos.Z = pos.Z - 1.; if (pos.Z<0.) pos.Z = pos.Z + 1.;
                }

                positions.push_back(std::min(std::max(pos.X, TOL), 1 - TOL)); // Clamp into the open unit cube for PVFMM.
                positions.push_back(std::min(std::max(pos.Y, TOL), 1 - TOL));
                positions.push_back(std::min(std::max(pos.Z, TOL), 1 - TOL));
            }
        }

        int pos_l = positions.size();
        for (auto n : positions){if (std::isnan(n)){ failed = true; std::cout<<"Failed because of position:"<<std::endl;}}

        // FMM
        size_t N = positions.size() / DIM;
        forces_.resize(N * DIM);

        if (!(timestep_%LR_STRIDE)) { // Refresh long-range forces on the configured stride.
            std::cout<<"LR COMP: "<<timestep_<<std::endl;
            std::cout<<"pos ="<<positions.size()<<", for ="<<forces_.size()<<", den ="<<densities_.size()<<std::endl;

            // Perform KIFMM summation of forces
            computeLRForces(positions, forces_, densities_, comm);

            // Normalize forces by target densities - PVFMM and BF routines do not do that by default
            for (int i = 0; i< densities_.size(); i++)
            {for (int k = 0; k<3; k++) forces_[i*DIM+k] *= densities_[i];}
            failed = false;
            for (auto& n : forces_){
                if (!std::isfinite(n)){failed = true; n = 0.0;} // Drop invalid FMM output before applying forces.
            }
            if (failed) std::cout<<"FAILED AT "<<timestep_<<std::endl;
        }

        if (!(timestep_%outputStride)) { // Save energy, fabric and platelet-normal diagnostics.

            double potEnergy = computeLRPotEnergy(positions, densities_, comm);
            PELog.push_back(LRMagnitude * potEnergy);
            std::cout<<"potEnergy: "<<potEnergy<<std::endl;

            std::ofstream out("LRPotential.txt");
            for (double x : PELog) {
                out << x << "\n";
            }

            const AnisotropySnapshot fabric = computeAnisotropySnapshot();
            appendAnisotropyLog(fabric);
            writePlateletNormalsVtp(fabric);
            writePlateletNormalsPvd();
            std::cout<<"anisotropy: S_z="<<fabric.Sz
                     <<", S_tensor="<<fabric.S_tensor
                     <<", validClumps="<<fabric.validClumps<<std::endl;
        }

        // Apply LR forces to particles

        int p = 0;
        for (std::vector<BaseParticle*>::iterator it= particleHandler.begin(); it!=particleHandler.end(); ++it){
            if ((*it)->isPebble()) {
                (*it)->setLongRangeForce(
                        Vec3D(LRMagnitude * forces_[p * DIM], LRMagnitude * forces_[p * DIM + 1], LRMagnitude * forces_[p * DIM + 2]));
                p++;
            }
        }
    }

private:
    int clump_index;
    ClumpData data;
    Mdouble clump_mass = 1;
    Mdouble clump_damping = 0;
};



int main(int argc, char* argv[])
{
#ifdef PVFMM_EXTENDED_BC
    std::cout<<"You ran CMAKE with PVFMM_EXTENDED_BC option, which makes PVFMM Periodic BCs unavailable"<<std::endl;
#endif

    MPI_Init(&argc, &argv);

    // Optional CLI:
    //   ./clay_anisotropy_updated <pH> <N_att> <timeMax> <seed>
    //
    // Defaults remain close to clay_anisotropy.cpp: seed=1 and no imposed initial rotation.
    if (argc > 1) {
        try { solutionPH = std::stod(argv[1]); } catch (...) {}
    }
    if (argc > 2) {
        try {
            const int requestedParticles = std::stoi(argv[2]);
            if (requestedParticles > 0) N_att = requestedParticles;
        } catch (...) {}
    }
    if (argc > 3) {
        try {
            const double requestedTimeMax = std::stod(argv[3]);
            if (requestedTimeMax > 0.0) timeMax = requestedTimeMax;
        } catch (...) {}
    }
    if (argc > 4) {
        try { randomSeed = static_cast<unsigned int>(std::stoul(argv[4])); } catch (...) {}
    }
    std::srand(randomSeed);

    outputStride = computeOutputStride(timeMax, timeStep, TARGET_OUTPUT_FRAMES);

    std::cout<<"Using solutionPH="<<solutionPH
             <<", N_att="<<N_att
             <<", timeMax="<<timeMax
             <<", randomSeed="<<randomSeed
             <<", timeStep="<<timeStep
             <<", targetOutputFrames="<<TARGET_OUTPUT_FRAMES
             <<", outputStride="<<outputStride
             <<std::endl;

    Clay problem;





    auto species = problem.speciesHandler.copyAndAddObject(LinearViscoelasticFrictionSpecies());
    species->setDensity(specDens); // sets the species type-0 density
    std::cout<<species->getConstantRestitution()<<std::endl;
    species->setDissipation(specDiss);
    species->setStiffness(specStif);
    const Mdouble collisionTime = species->getCollisionTime(problem.getClumpMass());
    std::cout<<"collisionTime/400 diagnostic: "<< collisionTime / 400
             <<", applied timeStep: "<<timeStep<<std::endl;
    problem.setClumpDamping(clumpDamping);
    problem.setTimeStep(timeStep);

    // Match MercuryDPM output cadence to the diagnostic cadence.
    problem.setSaveCount(outputStride);
    problem.setTimeMax(timeMax);

    // For this driver the ParaView VTU files, restart file, energy file and
    // custom CSV/PVD/VTP diagnostics contain the useful outputs. The default
    // Mercury .data/.fstat text files are very large for long runs, so disable
    // them unless they are specifically needed for legacy post-processing.
    problem.dataFile.setFileType(FileType::NO_FILE);
    problem.fStatFile.setFileType(FileType::NO_FILE);

    problem.removeOldFiles();
    problem.solve();

    // Shut down MPI
    MPI_Finalize();

    return 0;
}
