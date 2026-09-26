package com.example.focusquest.streak;

import java.time.Instant;
import java.util.List;

/**
 * The user's streak freezes: how many unused ones they hold and may hold, what one costs, their gem
 * balance, and the most recent days freezes covered, newest first, so the UI can say one was used.
 */
public record FreezeInventoryResponse(
        int owned,
        int maxOwned,
        int price,
        long gems,
        List<UsedFreeze> recentlyUsed
) {

    /** A freeze that was spent: the daily period it covered, and when it was spent. */
    public record UsedFreeze(Instant periodStart, Instant periodEnd, Instant usedAt) {
    }
}
