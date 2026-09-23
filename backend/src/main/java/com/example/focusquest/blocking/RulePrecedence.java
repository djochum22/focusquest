package com.example.focusquest.blocking;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Decides whether a URL is blocked given the active block and allowlist rules (mirrored by the
 * extension's {@code rulePrecedence.ts}).
 *
 * <p>Among all rules that match the URL, the most specific one wins (see
 * {@link UrlRule#compareTo}). When an allowlist rule and a block rule are equally specific, the
 * allowlist rule wins. This yields the documented order: most-specific allowlist, most-specific
 * block, broader allowlist, broader block, default allow.
 *
 * <p>Examples with block {@code example.com} and allow {@code example.com/docs}:
 * {@code example.com/docs/setup} is allowed and {@code example.com/forum} is blocked.
 */
public final class RulePrecedence {

    public enum Verdict {
        BLOCKED,
        ALLOWED_BY_ALLOWLIST,
        ALLOWED_BY_DEFAULT
    }

    /**
     * @param verdict     the outcome
     * @param matchedRule the rule that decided it; empty for {@link Verdict#ALLOWED_BY_DEFAULT}
     */
    public record Decision(Verdict verdict, Optional<UrlRule> matchedRule) {

        public boolean isBlocked() {
            return verdict == Verdict.BLOCKED;
        }
    }

    private RulePrecedence() {
    }

    public static Decision evaluate(TargetUrl url, Collection<UrlRule> blockRules, Collection<UrlRule> allowRules) {
        Optional<UrlRule> allow = mostSpecificMatch(url, allowRules);
        Optional<UrlRule> block = mostSpecificMatch(url, blockRules);

        if (allow.isPresent() && (block.isEmpty() || allow.get().compareTo(block.get()) >= 0)) {
            return new Decision(Verdict.ALLOWED_BY_ALLOWLIST, allow);
        }
        if (block.isPresent()) {
            return new Decision(Verdict.BLOCKED, block);
        }
        return new Decision(Verdict.ALLOWED_BY_DEFAULT, Optional.empty());
    }

    private static Optional<UrlRule> mostSpecificMatch(TargetUrl url, Collection<UrlRule> rules) {
        return rules.stream()
                .filter(rule -> rule.matches(url))
                .max(Comparator.naturalOrder());
    }
}
