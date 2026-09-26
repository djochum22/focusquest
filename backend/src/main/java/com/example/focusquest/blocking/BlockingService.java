package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.exception.DuplicateRuleException;
import com.example.focusquest.shared.exception.InvalidRuleException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Owns block and allowlist rules, decides whether enforcement is active, and builds the state the
 * Chrome extension synchronizes. The extension performs the actual per-URL blocking using the
 * rules and the precedence documented on {@link RulePrecedence}.
 */
@Service
public class BlockingService {

    private final BlockedTargetRepository blockedTargetRepository;
    private final AllowlistTargetRepository allowlistTargetRepository;
    private final SessionService sessionService;
    private final StreakService streakService;
    private final ClockProvider clockProvider;

    public BlockingService(BlockedTargetRepository blockedTargetRepository,
                            AllowlistTargetRepository allowlistTargetRepository,
                            SessionService sessionService,
                            StreakService streakService,
                            ClockProvider clockProvider) {
        this.blockedTargetRepository = blockedTargetRepository;
        this.allowlistTargetRepository = allowlistTargetRepository;
        this.sessionService = sessionService;
        this.streakService = streakService;
        this.clockProvider = clockProvider;
    }

    // --- blocked targets ---
    //
    // While a session holds enforcement the blocking configuration may only get stricter: adding a block
    // rule is allowed, but editing or deleting one (which could unblock a site) is refused.

    @Transactional(readOnly = true)
    public List<BlockedTarget> listBlockedTargets(User user) {
        return blockedTargetRepository.findByUserOrderByIdAsc(user);
    }

    @Transactional
    public BlockedTarget createBlockedTarget(User user, String rawValue, String displayName, Boolean active) {
        return create(blockedTargetRepository, BlockedTarget::new, user, rawValue, displayName, active);
    }

    @Transactional
    public BlockedTarget updateBlockedTarget(User user, Long id, String rawValue, String displayName, Boolean active) {
        requireConfigurationNotLocked(user);
        return update(blockedTargetRepository, user, id, rawValue, displayName, active);
    }

    @Transactional
    public void deleteBlockedTarget(User user, Long id) {
        requireConfigurationNotLocked(user);
        delete(blockedTargetRepository, user, id);
    }

    // --- allowlist targets ---
    //
    // The mirror image: an allowlist rule can only loosen blocking, so adding or editing one is
    // refused while enforcement is active, and deleting one (which tightens blocking) is allowed.

    @Transactional(readOnly = true)
    public List<AllowlistTarget> listAllowlistTargets(User user) {
        return allowlistTargetRepository.findByUserOrderByIdAsc(user);
    }

    @Transactional
    public AllowlistTarget createAllowlistTarget(User user, String rawValue, String displayName, Boolean active) {
        requireConfigurationNotLocked(user);
        return create(allowlistTargetRepository, AllowlistTarget::new, user, rawValue, displayName, active);
    }

    @Transactional
    public AllowlistTarget updateAllowlistTarget(User user, Long id, String rawValue, String displayName,
                                                  Boolean active) {
        requireConfigurationNotLocked(user);
        return update(allowlistTargetRepository, user, id, rawValue, displayName, active);
    }

    @Transactional
    public void deleteAllowlistTarget(User user, Long id) {
        delete(allowlistTargetRepository, user, id);
    }

    // --- enforcement and extension state ---

    /**
     * Whether websites are blocked right now. Blocking is on from the start of each day until the
     * daily streak target is reached, and throughout any running session:
     *
     * <ul>
     *   <li>ACTIVE and PAUSED sessions always enforce, even once the daily target is reached;
     *       pausing never releases blocking.</li>
     *   <li>Otherwise blocking is on while today's daily target is unmet, whether or not a session
     *       was ever started. Completing a session that leaves the target unmet keeps it on.</li>
     *   <li>The one way out before the target is reached is overriding a session abandoned today,
     *       which releases blocking for the rest of the day, or until a new session starts.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public boolean isEnforcementActive(User user) {
        Optional<FocusSession> latest = sessionService.findLatestStartedSession(user);
        if (latest.filter(this::isRunning).isPresent()) {
            return true;
        }
        return !streakService.isDailyTargetReached(user)
                && latest.filter(session -> isOverriddenToday(user, session)).isEmpty();
    }

    /**
     * The session holding website enforcement, if any: a running session, or one abandoned today
     * whose blocking has not been released or overridden while today's daily target is unmet.
     * Enforcement can be active without one (see {@link #isEnforcementActive}); this is the session
     * the blocked page reports, and while it exists the blocking and streak configuration cannot
     * be loosened.
     */
    @Transactional(readOnly = true)
    public Optional<FocusSession> findEnforcingSession(User user) {
        return sessionService.findLatestStartedSession(user).filter(session -> holdsEnforcement(user, session));
    }

    @Transactional(readOnly = true)
    public BlockingSnapshot getBlockingSnapshot(User user) {
        Instant now = clockProvider.now();
        if (!isEnforcementActive(user)) {
            return new BlockingSnapshot(false, null, List.of(), List.of(), fingerprint(false, null, List.of(), List.of()), now);
        }

        List<BlockedTarget> blockRules = blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user);
        List<AllowlistTarget> allowRules = allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user);
        FocusSession session = findEnforcingSession(user).orElse(null);
        return new BlockingSnapshot(true, session, blockRules, allowRules,
                fingerprint(true, session, blockRules, allowRules), now);
    }

    /**
     * Handles the extension's periodic check-in: records it against the running session (which may
     * interrupt that session if the previous check-in was too long ago) and returns the resulting
     * state, so an interruption reaches the extension in the same response.
     */
    @Transactional
    public BlockingSnapshot heartbeat(User user) {
        sessionService.recordHeartbeat(user);
        return getBlockingSnapshot(user);
    }

    /**
     * What the blocked page shows while enforcement is active: the enforcing session with live
     * progress, if there is one, and today's daily streak progress. Empty when nothing is enforced.
     */
    @Transactional
    public Optional<CurrentSessionSnapshot> getCurrentSession(User user) {
        if (!isEnforcementActive(user)) {
            return Optional.empty();
        }
        Instant now = clockProvider.now();
        StreakPeriod dailyProgress = streakService.getCurrentProgress(user, StreakPeriodType.DAILY).orElse(null);
        return Optional.of(findEnforcingSession(user)
                .map(session -> {
                    long activeSeconds = session.activeSecondsAt(now);
                    // Settled off-task time only; the web app shows the provisional part as it happens.
                    long remainingSeconds = Math.max(0,
                            session.getPlannedFocusMinutes() * 60L - (activeSeconds - session.getOffTaskSeconds()));
                    return new CurrentSessionSnapshot(session, activeSeconds, remainingSeconds, dailyProgress, now);
                })
                .orElseGet(() -> new CurrentSessionSnapshot(null, 0, 0, dailyProgress, now)));
    }

    /**
     * Applies the user's active rules to {@code url} using {@link RulePrecedence}. Reports what the
     * rules say only; whether enforcement is currently active is a separate question. URLs that are
     * not http(s) are never blocked.
     */
    @Transactional(readOnly = true)
    public RulePrecedence.Decision evaluateUrl(User user, String url) {
        Optional<TargetUrl> target = TargetUrl.parse(url);
        if (target.isEmpty()) {
            return new RulePrecedence.Decision(RulePrecedence.Verdict.ALLOWED_BY_DEFAULT, Optional.empty());
        }
        List<UrlRule> blockRules = blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user).stream()
                .map(RuleTarget::rule).toList();
        List<UrlRule> allowRules = allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user).stream()
                .map(RuleTarget::rule).toList();
        return RulePrecedence.evaluate(target.get(), blockRules, allowRules);
    }

    private boolean holdsEnforcement(User user, FocusSession session) {
        return switch (session.getStatus()) {
            case ACTIVE, PAUSED -> true;
            case ABANDONED -> session.getBlockingState() == BlockingState.ACTIVE
                    && !session.isOverrideUsed()
                    && streakService.isInCurrentDailyPeriod(user, session.getAbandonedAt())
                    && !streakService.isDailyTargetReached(user);
            case PLANNED, COMPLETED, INTERRUPTED -> false;
        };
    }

    private boolean isRunning(FocusSession session) {
        return session.getStatus() == SessionStatus.ACTIVE || session.getStatus() == SessionStatus.PAUSED;
    }

    private boolean isOverriddenToday(User user, FocusSession session) {
        return session.isOverrideUsed() && streakService.isInCurrentDailyPeriod(user, session.getAbandonedAt());
    }

    // --- shared rule CRUD ---

    @FunctionalInterface
    private interface TargetFactory<T extends RuleTarget> {
        T create(User user, UrlRule rule, String displayName, boolean active);
    }

    private <T extends RuleTarget> T create(RuleTargetRepository<T> repository, TargetFactory<T> factory,
                                             User user, String rawValue, String displayName, Boolean active) {
        UrlRule rule = normalize(rawValue);
        requireUnique(repository, user, rule, null);
        return repository.save(factory.create(user, rule, resolveDisplayName(displayName, rule), active == null || active));
    }

    private <T extends RuleTarget> T update(RuleTargetRepository<T> repository, User user, Long id,
                                             String rawValue, String displayName, Boolean active) {
        T target = getOwnedOrThrow(repository, user, id);
        UrlRule rule = normalize(rawValue);
        requireUnique(repository, user, rule, id);
        target.update(rule, resolveDisplayName(displayName, rule), active == null ? target.isActive() : active);
        return repository.save(target);
    }

    private <T extends RuleTarget> void delete(RuleTargetRepository<T> repository, User user, Long id) {
        repository.delete(getOwnedOrThrow(repository, user, id));
    }

    private <T extends RuleTarget> T getOwnedOrThrow(RuleTargetRepository<T> repository, User user, Long id) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
    }

    private UrlRule normalize(String rawValue) {
        try {
            return RuleNormalizer.normalize(rawValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidRuleException("targetValue " + e.getMessage());
        }
    }

    private <T extends RuleTarget> void requireUnique(RuleTargetRepository<T> repository, User user,
                                                       UrlRule rule, Long excludingId) {
        boolean duplicate = excludingId == null
                ? repository.existsByUserAndTargetValue(user, rule.value())
                : repository.existsByUserAndTargetValueAndIdNot(user, rule.value(), excludingId);
        if (duplicate) {
            throw new DuplicateRuleException("A rule for " + rule.value() + " already exists");
        }
    }

    private String resolveDisplayName(String displayName, UrlRule rule) {
        return displayName == null || displayName.isBlank() ? rule.value() : displayName.trim();
    }

    // Locked only while a session holds enforcement: blocking that comes from the daily target
    // alone lasts all day, and the user must still be able to correct their rules during it.
    private void requireConfigurationNotLocked(User user) {
        if (findEnforcingSession(user).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Blocking rules cannot be loosened while website blocking is active");
        }
    }

    private String fingerprint(boolean enforcementActive, FocusSession session,
                                List<BlockedTarget> blockRules, List<AllowlistTarget> allowRules) {
        StringBuilder canonical = new StringBuilder("enforcing=").append(enforcementActive);
        if (session != null) {
            canonical.append(";session=").append(session.getId());
            canonical.append(";blocking=").append(session.getBlockingState());
        }
        blockRules.forEach(rule -> canonical.append(";block=").append(rule.getId()).append(':').append(rule.getTargetValue()));
        allowRules.forEach(rule -> canonical.append(";allow=").append(rule.getId()).append(':').append(rule.getTargetValue()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
