package com.example.focusquest.progression;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records XP changes as ledger transactions. Only the manual-override penalty exists so far; the
 * completion award and XP balance queries arrive with the rest of the progression phase.
 */
@Service
public class ExperienceService {

    public static final String FOCUS_SESSION_REFERENCE = "FOCUS_SESSION";

    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final ClockProvider clockProvider;
    private final int manualOverridePenalty;

    public ExperienceService(ExperienceTransactionRepository experienceTransactionRepository,
                              ClockProvider clockProvider,
                              @Value("${focusquest.xp.manual-override-penalty}") int manualOverridePenalty) {
        if (manualOverridePenalty < 0) {
            throw new IllegalArgumentException("focusquest.xp.manual-override-penalty must not be negative");
        }
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.clockProvider = clockProvider;
        this.manualOverridePenalty = manualOverridePenalty;
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
}
