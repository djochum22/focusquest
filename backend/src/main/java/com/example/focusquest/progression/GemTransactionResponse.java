package com.example.focusquest.progression;

import java.time.Instant;

/** API representation of one gem ledger entry; {@code amount} is negative for spending. */
public record GemTransactionResponse(
        Long id,
        int amount,
        GemTransactionType type,
        String referenceType,
        Long referenceId,
        Instant createdAt
) {

    public static GemTransactionResponse from(GemTransaction transaction) {
        return new GemTransactionResponse(transaction.getId(), transaction.getAmount(), transaction.getType(),
                transaction.getReferenceType(), transaction.getReferenceId(), transaction.getCreatedAt());
    }
}
