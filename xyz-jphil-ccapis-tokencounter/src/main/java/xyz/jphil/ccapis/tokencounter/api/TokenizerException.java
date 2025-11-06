package xyz.jphil.ccapis.tokencounter.api;

/**
 * Exception thrown when tokenization operations fail.
 */
public class TokenizerException extends Exception {

    public TokenizerException(String message) {
        super(message);
    }

    public TokenizerException(String message, Throwable cause) {
        super(message, cause);
    }
}
