package model.exceptions;

/**
 * Base class for Skip-Bo domain exceptions.
 */
public class SkipBoException extends RuntimeException {
    public SkipBoException(String message) {
        super(message);
    }

    public SkipBoException(String message, Throwable cause) {
        super(message, cause);
    }
}
