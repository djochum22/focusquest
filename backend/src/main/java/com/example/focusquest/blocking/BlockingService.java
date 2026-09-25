package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.shared.exception.DuplicateRuleException;
import com.example.focusquest.shared.exception.InvalidRuleException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
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
    // While enforcement is active the blocking configuration may only get stricter: adding a block
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
     * The session currently holding website enforcement, if any.
     *
     * <ul>
     *   <li>ACTIVE and PAUSED sessions enforce; pausing never releases blocking.</li>
     *   <li>An ABANDONED session keeps enforcing until the daily streak target is reached, unless
     *       its blocking was explicitly released or overridden. Only the user's latest started
     *       session is considered, so starting a new session supersedes an old abandoned one.</li>
     *   <li>COMPLETED and INTERRUPTED sessions release blocking. An interruption is a technical
     *       failure, and it must not lock the user out of the web.</li>
     * </ul>
     *
     * <p>This is derived from session status rather than read from the stored blocking state alone,
     * because the session module does not yet record RELEASED on completion.
     */
    @Transactional(readOnly = true)
    public Optional<FocusSession> findEnforcingSession(User user) {
        return sessionService.findLatestStartedSession(user).filter(session -> holdsEnforcement(user, session));
    }

    @Transactional(readOnly = true)
    public BlockingSnapshot getBlockingSnapshot(User user) {
        Instant now = clockProvider.now();
        Optional<FocusSession> enforcing = findEnforcingSession(user);
        if (enforcing.isEmpty()) {
            return new BlockingSnapshot(false, null, List.of(), List.of(), fingerprint(false, null, List.of(), List.of()), now);
        }

        List<BlockedTarget> blockRules = blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user);
        List<AllowlistTarget> allowRules = allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user);
        FocusSession session = enforcing.get();
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

    /** The enforcing session with live progress, or empty when nothing is being enforced. */
    @Transactional(readOnly = true)
    public Optional<CurrentSessionSnapshot> getCurrentSession(User user) {
        Instant now = clockProvider.now();
        return findEnforcingSession(user).map(session -> {
            long activeSeconds = session.activeSecondsAt(now);
            long remainingSeconds = Math.max(0, session.getPlannedFocusMinutes() * 60L - activeSeconds);
            return new CurrentSessionSnapshot(session, activeSeconds, remainingSeconds,
                    streakService.findCurrentPeriod(user, StreakPeriodType.DAILY).orElse(null), now);
        });
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
                    && !streakService.isDailyTargetReached(user);
            case PLANNED, COMPLETED, INTERRUPTED -> false;
        };
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
