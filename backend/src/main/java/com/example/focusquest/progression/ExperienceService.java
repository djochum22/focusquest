package com.example.focusquest.progression;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records XP changes as ledger transactions and reports the resulting balance. Every award is tied
 * to the thing that earned it (a session, a streak period) and recorded at most once, so a retried
 * request can never pay twice. The amounts are configuration; see {@code focusquest.xp} and the
 * economy section of the architecture document.
 */
@Service
public class ExperienceService {

    public static final String FOCUS_SESSION_REFERENCE = "FOCUS_SESSION";
    public static final String STREAK_PERIOD_REFERENCE = "STREAK_PERIOD";

    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final ClockProvider clockProvider;
    private final int manualOverridePenalty;
    private final int xpPerFocusMinute;
    private final int dailyStreakBonus;
    private final int weeklyStreakBonus;

    public ExperienceService(ExperienceTransactionRepository experienceTransactionRepository,
                              ClockProvider clockProvider,
                              @Value("${focusquest.xp.manual-override-penalty}") int manualOverridePenalty,
                              @Value("${focusquest.xp.per-focus-minute}") int xpPerFocusMinute,
                              @Value("${focusquest.xp.daily-streak-bonus}") int dailyStreakBonus,
                              @Value("${focusquest.xp.weekly-streak-bonus}") int weeklyStreakBonus) {
        if (manualOverridePenalty < 0 || xpPerFocusMinute < 0 || dailyStreakBonus < 0 || weeklyStreakBonus < 0) {
            throw new IllegalArgumentException("focusquest.xp.* amounts must not be negative");
        }
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.clockProvider = clockProvider;
        this.manualOverridePenalty = manualOverridePenalty;
        this.xpPerFocusMinute = xpPerFocusMinute;
        this.dailyStreakBonus = dailyStreakBonus;
        this.weeklyStreakBonus = weeklyStreakBonus;
    }

    /**
     * Awards XP for completing a session: {@code xpPerFocusMinute} for each planned minute. It is
     * the planned length, not the time focused, that is paid, and time beyond the plan earns
     * nothing; completing requires having focused the whole plan. Returns whether XP was newly
     * recorded (false when this session was already rewarded).
     */
    @Transactional
    public boolean awardSessionCompletion(User user, Long sessionId, int plannedFocusMinutes) {
        return record(user, plannedFocusMinutes * xpPerFocusMinute, ExperienceTransactionType.SESSION_COMPLETION,
                FOCUS_SESSION_REFERENCE, sessionId);
    }

    /** Awards the bonus for a streak period reaching its target. Returns whether XP was newly recorded. */
    @Transactional
    public boolean awardStreakCompletion(User user, StreakPeriodType periodType, Long periodId) {
        int amount = periodType == StreakPeriodType.DAILY ? dailyStreakBonus : weeklyStreakBonus;
        return record(user, amount, ExperienceTransactionType.STREAK_COMPLETION, STREAK_PERIOD_REFERENCE, periodId);
    }

    private boolean record(User user, int amount, ExperienceTransactionType type, String referenceType,
                            Long referenceId) {
        if (amount <= 0 || experienceTransactionRepository
                .findByUserAndTypeAndReferenceTypeAndReferenceId(user, type, referenceType, referenceId)
                .isPresent()) {
            return false;
        }
        experienceTransactionRepository.save(
                new ExperienceTransaction(user, amount, type, referenceType, referenceId, clockProvider.now()));
        return true;
    }

    /**
     * Penalizes the user's XP for overriding the given session. Idempotent per session: if the
     * penalty was already recorded, the existing transaction is returned instead of a second one.
     */
    @Transactional
    public ExperienceTransaction applyManualOverridePenalty(User user, Long sessionId) {
        return experienceTransactionRepository
                .findByUserAndTypeAndReferenceTypeAndReferenceId(
                        user, ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY, FOCUS_SESSION_REFERENCE, sessionId)
                .orElseGet(() -> experienceTransactionRepository.save(new ExperienceTransaction(
                        user, -manualOverridePenalty, ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY,
                        FOCUS_SESSION_REFERENCE, sessionId, clockProvider.now())));
    }

    /**
     * The user's XP: the sum of their ledger, never below zero. A penalty is recorded in full, but
     * a total below zero would read as a debt, so it is reported as zero.
     */
    @Transactional(readOnly = true)
    public long getTotalXp(User user) {
        return Math.max(0, experienceTransactionRepository.sumAmountByUser(user));
    }
}
