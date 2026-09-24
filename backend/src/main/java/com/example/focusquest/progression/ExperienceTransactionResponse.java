package com.example.focusquest.progression;

import java.time.Instant;

/** API representation of one XP ledger entry; {@code amount} is negative for a penalty. */
public record ExperienceTransactionResponse(
        Long id,
        int amount,
        ExperienceTransactionType type,
        String referenceType,
        Long referenceId,
        Instant createdAt
) {

    public static ExperienceTransactionResponse from(ExperienceTransaction transaction) {
        return new ExperienceTransactionResponse(transaction.getId(), transaction.getAmount(),
                transaction.getType(), transaction.getReferenceType(), transaction.getReferenceId(),
                transaction.getCreatedAt());
    }
}
