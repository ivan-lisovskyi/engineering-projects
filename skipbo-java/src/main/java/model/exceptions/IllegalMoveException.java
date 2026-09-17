package model.exceptions;

/**
 * Thrown when a well-formed move violates Skip-Bo rules.
 */
public class IllegalMoveException extends SkipBoException {
    public IllegalMoveException(String message) {
        super(message);
    }
}
