#include <iostream>   // Input and output stream
#include <random>      // Random number generation
#include <termios.h>   // Terminal
#include <unistd.h>    // UNIX standard functions

using namespace std; // Reducing std :: ...

// Class snake game  //
class SnakeGame {  // Main principle was adapted from https://www.geeksforgeeks.org/snake-code-cpp/
public:
    SnakeGame();        // Constructor for initializing a SnakeGame object
    ~SnakeGame();       // Destructor for cleaning up resources when a SnakeGame object is destroyed
    void RunGame();     // Function to run the game

private:
    int width;          // Width of the field
    int height;         // Height of the field
    int x, y;           // Current position of the snake's head
    int fruitX, fruitY;   // Coordinates of the fruit
    int score;    // Player's score
    int* tailX;    // Dynamic array to store X coordinates of snake's tail
    int* tailY;    // Dynamic array to store Y coordinates of snake's tail
    int tailLen;   // Length of the snake's tail
    enum Direction { STOP = 0, LEFT, RIGHT, UP, DOWN };   // Enumeratior for snake's state of the movement
    Direction sDir;   // Current direction of the snake
    bool isGameOver;        // Flag to check if the game is over

    void GameInit();        // Function to initialize the game
    void GameRender(string playerName);   // Function to render the game
    void UpdateGame();      // Function to update the game state
    char getChar();         // Function to get a character from the terminal
    void UserInput();       // Function to handle user input

    mt19937 gen;            // Random number generator
};

SnakeGame::SnakeGame() : gen(std::random_device()()), tailX(new int[100]), tailY(new int[100]) {
    width = 40;            // Set default width
    height = 10;           // Set default height
    tailLen = 0;      // Initialize snake's tail length to 0
}

SnakeGame::~SnakeGame() {
    delete[] tailX;    // Free memory for snake's tail (X coordinates)
    delete[] tailY;    // Free memory for snake's tail (Y coordinates)
}

void SnakeGame::RunGame() {
    string playerName;      // Player's name
    cout << "Enter your name: ";
    cin >> playerName;      // Get player's name from user
    GameInit();             // Initialize the game

    while (!isGameOver) {   // Continue running the game until it's over
        GameRender(playerName);   // Render the game
        UserInput();         // Handle user input
        UpdateGame();        // Update the game state
        usleep(150000);         // Delay of 150 milliseconds
    }

    GameRender(playerName);   // Render one last time to show the final state
}

void SnakeGame::GameInit() {
    isGameOver = false;     // Set game over flag to false
    sDir = STOP;            // Set snake's direction to STOP
    x = width / 2;          // Set initial X coordinate of snake's head
    y = height / 2;         // Set initial Y coordinate of snake's head

    // Do-while loop to generate fruit on the field (not on the snake)
    do {
        fruitX = rand() % width;    // Using modulo operator to ensure the random X coordinate is within the game's width
        fruitY = rand() % height;   // Using modulo operator to ensure the random Y coordinate is within the game's height
    } while (fruitX == x && fruitX == y);   // Repeat if fruit is generated at snake's head

    score = 0;        // Initialize player's score to 0
}

void SnakeGame::GameRender(string playerName) {
    cout << string(50, '\n');   // Clear the terminal

    for (int i = 0; i < width + 2; i++)
        cout << "-";    // Print top wall

    cout << endl;

    for (int i = 0; i < height; i++) {
        for (int j = 0; j <= width; j++) {
            if (j == 0 || j == width)
                cout << "|";    // Print side walls
            else if (i == y && j == x)
                cout << "O";    // Print snake's head
            else if (i == fruitY && j == fruitX)
                cout << "#";    // Print fruit
            else {
                bool prTail = false;
                for (int k = 0; k < tailLen; k++) {
                    if (tailX[k] == j && tailY[k] == i) {
                        cout << "@";   // Print snake's tail
                        prTail = true;
                    }
                }
                if (!prTail)
                    cout << " ";    // Print empty space
            }
        }
        cout << endl;
    }

    for (int i = 0; i < width + 2; i++)
        cout << "-";    // Print bottom boundary



    cout << endl;

    cout << playerName << "'s Score: " << score << endl;   // Display player's score
    cout << "Press Q to quit the game" << endl;   // Prompt to quit the game
}

void SnakeGame::UpdateGame() {
    int prevX = tailX[0];   // Store the previous X coordinate of the snake's head
    int prevY = tailY[0];   // Store the previous Y coordinate of the snake's head
    int prev2X, prev2Y;           // Variables to store temporary tail coordinates
    tailX[0] = x;            // Update the first element of the snake's tail with the new X coordinate
    tailY[0] = y;            // Update the first element of the snake's tail with the new Y coordinate

    for (int i = 1; i < tailLen; i++) {
        prev2X = tailX[i];   // Store the current X coordinate of the tail segment
        prev2Y = tailY[i];   // Store the current Y coordinate of the tail segment
        tailX[i] = prevX;    // Update the current tail segment with the previous X coordinate
        tailY[i] = prevY;    // Update the current tail segment with the previous Y coordinate
        prevX = prev2X;           // Update the previous X coordinate for the next iteration
        prevY = prev2Y;           // Update the previous Y coordinate for the next iteration
    }

    switch (sDir) {
        case STOP:
            break;
        case LEFT:
            x--;                  // Move the snake's head to the left
            break;
        case RIGHT:
            x++;                  // Move the snake's head to the right
            break;
        case UP:
            y--;                  // Move the snake's head upward
            break;
        case DOWN:
            y++;                  // Move the snake's head downward
            break;
    }

    if (x >= width) x = 0;        // Wrap around to the beginning if the snake's head goes beyond the width
    else if (x < 0) x = width - 1;   // Wrap around to the end if the snake's head goes before 0

    if (y >= height) y = 0;       // Wrap around to the top if the snake's head goes beyond the height
    else if (y < 0) y = height - 1;   // Wrap around to the bottom if the snake's head goes before 0

    for (int i = 0; i < tailLen; i++) {
        if (tailX[i] == x && tailY[i] == y)
            isGameOver = true;      // Check if the snake collided with itself
    }

    if (x == fruitX && y == fruitY) {
        score += 10;         // Increase player's score when the snake eats the fruit
        std::uniform_int_distribution<> disX(0, width - 1);
        std::uniform_int_distribution<> disY(0, height - 1);

        do {
            fruitX = disX(gen);   // Generate a new X coordinate for the fruit
            fruitY = disY(gen);   // Generate a new Y coordinate for the fruit
        } while (fruitX == x && fruitY == y);   // Repeat if the fruit is generated at the snake's head

        tailLen++;   // Increase the length of the snake's tail
    }
}
// https://pubs.opengroup.org/onlinepubs/7908799/xsh/termios.h.html
// using termios.h and chatGpt to get a readout through the terminal
char SnakeGame::getChar() {
    char buf = 0;   // Variable to store the read character
    struct termios old = {0};   // Structure to store the old terminal attributes
    fflush(stdout);
    if (tcgetattr(0, &old) < 0)
        perror("tcsetattr()");   // Get the current terminal attributes and check for errors
    old.c_lflag &= ~ICANON;   // Disable canonical mode (line buffering)
    old.c_lflag &= ~ECHO;   // Disable echoing of characters
    old.c_cc[VMIN] = 1;   // Set the minimum number of characters to read to 1
    old.c_cc[VTIME] = 0;   // Set the timeout to 0 (no timeout)
    if (tcsetattr(0, TCSANOW, &old) < 0)
        perror("tcsetattr ICANON");   // Set the modified terminal attributes and check for errors
    if (read(0, &buf, 1) < 0)
        perror("read()");   // Read a single character from the terminal
    old.c_lflag |= ICANON;   // Restore canonical mode
    old.c_lflag |= ECHO;   // Restore echoing of characters
    if (tcsetattr(0, TCSADRAIN, &old) < 0)
        perror("tcsetattr ~ICANON");   // Set the modified terminal attributes and wait for pending output to be written
    return buf;   // Return the read character
}


void SnakeGame::UserInput() {
    char keyPressed = getChar();   // Get a character from the terminal

    if (keyPressed) {
        switch (keyPressed) {
            case 'a':
                sDir = LEFT;   // Set snake's direction to left
                break;
            case 'd':
                sDir = RIGHT;   // Set snake's direction to right
                break;
            case 'w':
                sDir = UP;   // Set snake's direction to up
                break;
            case 's':
                sDir = DOWN;   // Set snake's direction to down
                break;
            case 'q':
                isGameOver = true;   // Quit the game
                break;
                // Capslock case
            case 'A':
                sDir = LEFT;   // Set snake's direction to left
                break;
            case 'D':
                sDir = RIGHT;   // Set snake's direction to right
                break;
            case 'W':
                sDir = UP;   // Set snake's direction to up
                break;
            case 'S':
                sDir = DOWN;   // Set snake's direction to down
                break;
            case 'Q':
                isGameOver = true;   // Quit the game
                break;
            default:
                sDir = STOP;
                break;
        }
    }
}

int main() {
    SnakeGame game;
    game.RunGame();
    return 0;
}
