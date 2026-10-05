package io.github.hanshs474.veida;

/**
 * Every failure the service can report, with a {@link Reason} to branch on:
 * wait, reword, or retry are different fixes and the message alone does not
 * tell them apart.
 */
public class VeidaException extends Exception {
    private static final long serialVersionUID = 1L;

    /** What went wrong, in terms of what the caller should do next. */
    public enum Reason {
        /** The free allowance is spent for this machine today. Wait, or sign in at https://veida.ai/pricing. */
        QUOTA,
        /** The content filter refused the prompt. Reword it; retrying the same text does not help. */
        REJECTED,
        /** The deadline passed before the image was ready. Safe to retry. */
        TIMEOUT,
        /** Anything else: a bad argument, a network error, an unexpected response. */
        OTHER
    }

    private final Reason reason;

    public VeidaException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public VeidaException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return what the caller should do about it */
    public Reason reason() {
        return reason;
    }
}
