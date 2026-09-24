package com.example.focusquest.progression;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records gem rewards as ledger transactions and reports the balance. Every reward is tied to the
 * thing that earned it (a level, a streak period) and recorded at most once, so a retried request
 * or a level lost and regained never pays twice.
 */
@Service
public class GemService {

    public static final String LEVEL_REFERENCE = "LEVEL";
    public static final String STREAK_PERIOD_REFERENCE = "STREAK_PERIOD";

    private final GemTransactionRepository gemTransactionRepository;
    private final ClockProvider clockProvider;
    private final int perLevel;
    private final int dailyStreakReward;
    private final int weeklyStreakReward;

    public GemService(GemTransactionRepository gemTransactionRepository,
                       ClockProvider clockProvider,
                       @Value("${focusquest.gems.per-level}") int perLevel,
                       @Value("${focusquest.gems.daily-streak}") int dailyStreakReward,
                       @Value("${focusquest.gems.weekly-streak}") int weeklyStreakReward) {
        if (perLevel < 0 || dailyStreakReward < 0 || weeklyStreakReward < 0) {
            throw new IllegalArgumentException("focusquest.gems.* rewards must not be negative");
        }
        this.gemTransactionRepository = gemTransactionRepository;
        this.clockProvider = clockProvider;
        this.perLevel = perLevel;
        this.dailyStreakReward = dailyStreakReward;
        this.weeklyStreakReward = weeklyStreakReward;
    }

    /** Grants the level-up reward for every level in {@code (fromLevel, toLevel]} not already rewarded. */
    @Transactional
    public void awardLevelUps(User user, int fromLevel, int toLevel) {
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            record(user, perLevel, GemTransactionType.LEVEL_UP, LEVEL_REFERENCE, (long) level);
        }
    }

    /** Grants the streak reward for a period that reached its target. Idempotent per period. */
    @Transactional
    public void awardStreakCompletion(User user, StreakPeriodType periodType, Long periodId) {
        int amount = periodType == StreakPeriodType.DAILY ? dailyStreakReward : weeklyStreakReward;
        record(user, amount, GemTransactionType.STREAK_COMPLETION, STREAK_PERIOD_REFERENCE, periodId);
    }

    @Transactional(readOnly = true)
    public long getBalance(User user) {
        return gemTransactionRepository.sumAmountByUser(user);
    }

    private void record(User user, int amount, GemTransactionType type, String referenceType, Long referenceId) {
        if (amount <= 0
                || gemTransactionRepository.existsByUserAndTypeAndReferenceTypeAndReferenceId(
                        user, type, referenceType, referenceId)) {
            return;
        }
        gemTransactionRepository.save(
                new GemTransaction(user, amount, type, referenceType, referenceId, clockProvider.now()));
    }
}
