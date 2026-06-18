package de.verdox.pv_miner.lightning;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.time.LocalDateTime;

@RegisterReflectionForBinding({LightningTransaction.class})
public record LightningTransaction(
        String id,
        String bolt11,
        long amountSat,
        String memo,
        Status status,
        Type type,
        LocalDateTime timestamp
) {
    public enum Status { PENDING, SETTLED, EXPIRED }
    public enum Type { INCOMING, OUTGOING }
}
