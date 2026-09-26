package com.example.focusquest.export;

import com.example.focusquest.streak.StreakFreeze;

import java.time.Instant;

/** A streak freeze. {@code usedPeriodId} and {@code usedAt} are null while it is unused. */
public record StreakFreezeBackup(
        Long id,
        Instant purchasedAt,
        Long usedPeriodId,
        Instant usedAt
) {

    static StreakFreezeBackup from(StreakFreeze freeze) {
        return new StreakFreezeBackup(freeze.getId(), freeze.getPurchasedAt(),
                freeze.isUsed() ? freeze.getUsedPeriod().getId() : null, freeze.getUsedAt());
    }
}
