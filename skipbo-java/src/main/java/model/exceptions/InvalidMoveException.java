package model.exceptions;

/**
 * Thrown when a move is malformed or references invalid piles or indices.
 */
public class InvalidMoveException extends SkipBoException {
    public InvalidMoveException(String message) {
        super(message);
    }
}
