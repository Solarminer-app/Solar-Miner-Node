package de.verdox.pv_miner.core.miner.braiins.graphql;

public class BosminerUnavailableException extends RuntimeException {
    public BosminerUnavailableException() {
    }

    public BosminerUnavailableException(String message) {
        super(message);
    }

    public BosminerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public BosminerUnavailableException(Throwable cause) {
        super(cause);
    }

    public BosminerUnavailableException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
