# Snake in C++

We built this terminal version of Snake for a C++ programming course. Move with WASD, collect fruit and avoid the snake's tail. The board, snake and score are drawn with text characters.

The project was made by Dmitrii Guselnikov and Ivan Lisovskyi. The whole program is in [snake.cpp](snake.cpp), with a class for the game state and methods for drawing, keyboard input and movement.

## How it works

The `SnakeGame` class holds the game state and has separate methods to set up the board, draw it, read a key and update the snake. The head is `O`, the tail is `@` and fruit is `#`. Each fruit adds ten points and one tail segment. The snake wraps around the board edges, and hitting its own tail ends the game.

Keyboard input uses `termios` and `read`. Input is blocking, so the game takes a step when a key is pressed, followed by a 150 ms delay. It does not move continuously between keypresses.

## Build and run

You need macOS or Linux, an interactive terminal and a C++11 compiler. The code uses `termios.h` and `unistd.h`; it is not a native Windows/MSVC program. WSL has not been tested.

From this project's directory, build with Clang:

```sh
mkdir -p build
clang++ -std=c++11 -Wall -Wextra -pedantic snake.cpp -o build/snake
```

Or use GCC:

```sh
mkdir -p build
g++ -std=c++11 -Wall -Wextra -pedantic snake.cpp -o build/snake
```

The retained code has memory-handling bugs, listed below. To try it after reviewing those notes:

```sh
./build/snake
```

Enter a single-word player name. Use **W/A/S/D** for up/left/down/right and **Q** to quit; lowercase keys work too.

Use only WASD and Q: other keys can end the game once the snake has a tail. Run it in a real terminal, not through redirected input. If an interrupted game leaves your terminal behaving strangely, run `stty sane` in that terminal.

## Known limitations

The submitted version has a few unresolved bugs:

- The tail arrays have room for 100 entries, but there is no check before adding another segment. A long game can access memory outside those arrays.
- `UpdateGame()` can read tail coordinates before they are initialized. This is undefined behaviour.
- The first fruit-position check compares `fruitX` with both head coordinates. Later fruit placement avoids the head but can still overlap the tail.
- The board coordinates and drawn walls do not always line up, so the border can hide a position.
- Terminal settings are not fully saved and restored on every exit path.

A next version should start with safe tail storage, initialized coordinates and corrected fruit placement. Separating the game rules from terminal input would also make them easier to test.

## Build check

Compiled with Apple Clang on macOS on 17 September 2026, with warnings about member-initialization order and partial `termios` initialization. Gameplay was not retested, and there are no automated tests.

## Credits

The source credits a [GeeksforGeeks Snake example](https://www.geeksforgeeks.org/snake-code-cpp/) for the main approach and links to [the Open Group `termios.h` reference](https://pubs.opengroup.org/onlinepubs/7908799/xsh/termios.h.html). Its comments also record ChatGPT help with terminal input. These credits are kept in the code.

The code is unchanged from the submission. See the [project notes](../docs/PROJECT_NOTES.md) for details about this copy and reuse permissions.
