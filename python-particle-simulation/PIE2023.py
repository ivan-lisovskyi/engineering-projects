import numpy as np


class SimulationConfig:
    """
    Configuration parameters for the particle simulation.

    Attributes:
    - num_particles (int): Number of particles. Default is 50.
    - k (float): Spring constant. Default is 1.0.
    - m (float): Mass of particles. Default is 1.0.
    - timesteps (int): Number of timesteps. Default is 1000.
    - boundary_min (float): Minimum boundary value. Default is 0.
    - boundary_max (float): Maximum boundary value. Default is 1.
    - radius (float): Radius of particles. Default is 1.0.
    - dt (float): Time step size for the simulation, calculated based on m and k.
    """

    def __init__(self, num_particles=50, k=1.0, m=1.0, timesteps=1000, boundary_min=0, boundary_max=1, radius=1.0):
        """
        Initializes the configuration with given parameters and validates them.
        """
        self.num_particles = num_particles
        self.k = k
        self.m = m
        self.timesteps = timesteps
        self.boundary_min = boundary_min
        self.boundary_max = boundary_max
        self.radius = radius
        self.validate_parameters()
        self.dt = 0.01 * np.sqrt(self.m / self.k)  # Stable timestep calculation

    def validate_parameters(self):
        """Validates the parameters for the simulation."""
        if not isinstance(self.num_particles, int) or self.num_particles <= 0:
            raise ValueError("Number of particles must be a positive integer.")
        if not isinstance(self.k, (int, float)) or self.k <= 0:
            raise ValueError("Spring constant must be a positive number.")
        if not isinstance(self.m, (int, float)) or self.m <= 0:
            raise ValueError("Mass of particles must be a positive number.")
        if not isinstance(self.timesteps, int) or self.timesteps <= 0:
            raise ValueError("Number of timesteps must be a positive integer.")
        if not isinstance(self.boundary_min, (int, float)):
            raise ValueError("Minimum boundary must be a number.")
        if not isinstance(self.boundary_max, (int, float)):
            raise ValueError("Maximum boundary must be a number.")
        if self.boundary_min > self.boundary_max:
            raise ValueError("Minimum boundary must be less than or equal to maximum boundary.")
        if not isinstance(self.radius, (int, float)) or self.radius <= 0:
            raise ValueError("Radius must be a positive number.")


class ParticleSimulation:
    """
    Simulates particle dynamics using the Velocity Verlet method.

    Attributes:
    - GRAVITY (np.ndarray): Gravitational force.
    - config (SimulationConfig): Configuration object for the simulation.
    - positions (np.ndarray): Positions of the particles.
    - velocities (np.ndarray): Velocities of the particles.
    - forces (np.ndarray): Forces acting on the particles.
    """

    GRAVITY = np.array([0, -9.81])  # Gravitational force in the negative y-direction

    def __init__(self, config):
        """
        Initializes the simulation with the given configuration.
        """
        self.config = config
        self.positions = self._init_positions()
        self.velocities = np.zeros((self.config.num_particles, 2))
        self.forces = np.zeros((self.config.num_particles, 2))
        self.computeForces()

    def _init_positions(self):
        """Initializes particle positions randomly within the upper half of the boundary box."""
        positions = np.random.rand(self.config.num_particles, 2)
        positions[:, 1] += self.config.boundary_max / 2
        return positions

    def start_simulation(self):
        """Runs the simulation for the specified number of timesteps."""
        results = []
        for step in range(self.config.timesteps):
            self.velocityVerlet()
            if step % (self.config.timesteps // 5) == 0:  # Collect data every 1/5th of total timesteps
                results.append((step, self.positions.copy(), self.velocities.copy()))
                print(f"Running timestep {step} / {self.config.timesteps}")
        self.displayResults(results)

    def displayResults(self, results):
        """Saves the simulation results to a file."""
        try:
            with open("simulation_results.txt", "w") as f:
                for step, positions, velocities in results:
                    f.write(f"Timestep {step}:\n")
                    f.write("Positions:\n")
                    np.savetxt(f, positions)
                    f.write("Velocities:\n")
                    np.savetxt(f, velocities)
                    f.write("----" * 10 + "\n")
            print("Results have been written to simulation_results.txt")
        except IOError as io_err:
            print(f"File error: {io_err}")

    def computeForces(self):
        """Computes the forces between particles and applies gravity."""
        self.forces.fill(0.0)   # reset
        self.forces[:, 1] += self.GRAVITY[1] * self.config.m  # Applying gravity to each particle
        for i in range(self.config.num_particles): # Calculate forces
            for j in range(i + 1, self.config.num_particles):
                self.handleParticleInteraction(i, j) # The interaction force between particle i and particle j

    def handleParticleInteraction(self, i, j):
        """
        Handles the interaction between two particles and computes the resulting forces.

        Parameters:
        - i (int): Index of the first particle.
        - j (int): Index of the second particle.
        """
        r_ij = self.positions[j] - self.positions[i]   # Compute the vector distance between particles i and j
        dist = np.linalg.norm(r_ij)   # Compute the scalar distance between particles i and j
        if dist < 1e-6:  # Avoid division by zero
            return
        if dist < 2 * self.config.radius:
            overlap = 2 * self.config.radius - dist   # Calculate the overlap distance
            force = self.config.k * overlap * r_ij / dist   # Apply Hooke's Law to calculate the force
            # Update the forces acting on both particles
            self.forces[i] -= force
            self.forces[j] += force

    def applyBoundaryConditions(self):
        """Ensures particles stay within the boundaries of the rectangular box by reflecting velocities."""
        self.positions = np.clip(self.positions, self.config.boundary_min, self.config.boundary_max)
        self.velocities = np.where((self.positions == self.config.boundary_min) |
                                   (self.positions == self.config.boundary_max), -self.velocities, self.velocities)

    def velocityVerlet(self):
        """Performs the velocity Verlet integration method to update particle positions and velocities."""
        self.velocities += 0.5 * self.forces * self.config.dt / self.config.m
        self.positions += self.velocities * self.config.dt
        self.applyBoundaryConditions()    # Ensuring particles stay within the simulation box
        self.computeForces()     # Recompute forces based on the new positions
        self.velocities += 0.5 * self.forces * self.config.dt / self.config.m


if __name__ == '__main__':
    try:
        config = SimulationConfig()  # Initialize configuration with default parameters
        simulation = ParticleSimulation(config)  # Create simulation object
        simulation.start_simulation()  # Start the simulation
    except ValueError as ve:
        print(f"Configuration Error: {ve}")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

