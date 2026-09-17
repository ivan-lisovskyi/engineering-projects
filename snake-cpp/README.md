# Snake in C++

A small Snake game made for a programming course. It runs in the terminal: move with WASD, collect fruit to grow the snake and avoid running into its tail. The board, snake and score are all drawn with text characters.

The project was made by Dmitrii Guselnikov and Ivan Lisovskyi. The original submission uses the spelling “Lisovskiy”. This is our shared coursework, not an individual project.

The whole program is in [snake.cpp](snake.cpp). It is an early C++ project covering classes, terminal input, arrays and collision checks. The source is kept as submitted, including the issues listed below.

## How it works

The `SnakeGame` class holds the game state and has separate methods to set up the board, draw it, read a key and update the snake. The head is `O`, the tail is `@` and fruit is `#`. Each fruit adds ten points and one tail segment. The snake wraps around the board edges, and hitting its own tail ends the game.

Keyboard input uses `termios` and `read`. Input is blocking, so the game takes a step when a key is pressed, followed by a 150 ms delay. It does not move continuously between keypresses.

## Build and run

You need macOS or Linux, an interactive terminal and a compiler that supports C++11. The code uses `termios.h` and `unistd.h`; it will not build as a native Windows/MSVC program. WSL has not been tested.

From this project's directory, build with Clang:

```sh
mkdir -p build
clang++ -std=c++11 -Wall -Wextra -pedantic snake.cpp -o build/snake
```

Or use GCC:

```sh
g++ -std=c++11 -Wall -Wextra -pedantic snake.cpp -o build/snake
```

The source compiled with Apple Clang on macOS on 17 September 2026. There were two warnings, about member-initialization order and partial `termios` initialization. This was a build check, not a gameplay test; the project has no automated tests.

Read the limitations below before running the original program:

```sh
./build/snake
```

Enter a single-word player name, then use the following keys. Both lower- and uppercase are accepted.

| Key | Action |
|---|---|
| W | Up |
| A | Left |
| S | Down |
| D | Right |
| Q | Quit |

Other keys stop the snake's movement. Run the game in a real terminal, not through redirected input. If an interrupted game leaves your terminal behaving strangely, run `stty sane` in that terminal.

## Known limitations

The original program has several bugs that should be fixed before treating it as a finished game:

- The tail arrays have room for 100 entries, but there is no check before adding another segment. A long game can access memory outside those arrays.
- `UpdateGame()` can read tail coordinates before they are initialized. This is undefined behaviour.
- The first fruit-position check compares `fruitX` with both head coordinates. Later fruit placement avoids the head but can still overlap the tail.
- The board coordinates and drawn walls do not always line up, so the border can hide a position.
- Terminal settings are not fully saved and restored on every exit path.

A next version should start with safe tail storage, initialized coordinates and corrected fruit placement. Separating the game rules from terminal input would also make them easier to test. These changes should be kept separate from the original submission.

## Attribution and reuse

The source credits a [GeeksforGeeks Snake example](https://www.geeksforgeeks.org/snake-code-cpp/) for the main approach and links to [the Open Group `termios.h` reference](https://pubs.opengroup.org/onlinepubs/7908799/xsh/termios.h.html). Its comments also record ChatGPT help with terminal input. These credits are kept in the code.

The authors' names come from the original submission README. Student numbers and IDE files are not included. This README and `.gitignore` were added when preparing the project for GitHub. There is no new open-source licence; teammate permission and the terms for adapted code need to be checked before public redistribution.
