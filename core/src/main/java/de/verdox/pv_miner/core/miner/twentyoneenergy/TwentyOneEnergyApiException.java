package de.verdox.pv_miner.core.miner.twentyoneenergy;

/** A deliberately small, non-sensitive error boundary for the local 21control API. */
public class TwentyOneEnergyApiException extends RuntimeException {
    public TwentyOneEnergyApiException(String message) {
        super(message);
    }

    public TwentyOneEnergyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
