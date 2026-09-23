package com.example.focusquest.blocking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.focusquest.blocking.RulePrecedence.Decision;
import com.example.focusquest.blocking.RulePrecedence.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Allowlist and block precedence: most-specific matching rule wins; allowlist wins ties. */
class RulePrecedenceTest {

    private static List<UrlRule> rules(String... values) {
        return java.util.Arrays.stream(values).map(RuleNormalizer::normalize).toList();
    }

    private static Decision evaluate(String url, List<UrlRule> block, List<UrlRule> allow) {
        return RulePrecedence.evaluate(TargetUrl.parse(url).orElseThrow(), block, allow);
    }

    @Test
    void defaultAllowsWhenNoRuleMatches() {
        Decision decision = evaluate("https://example.org/", rules("example.com"), rules("docs.example.com"));

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOWED_BY_DEFAULT);
        assertThat(decision.matchedRule()).isEmpty();
        assertThat(decision.isBlocked()).isFalse();
    }

    @Test
    void defaultAllowsWhenThereAreNoRulesAtAll() {
        assertThat(evaluate("https://example.com/", rules(), rules()).verdict()).isEqualTo(Verdict.ALLOWED_BY_DEFAULT);
    }

    @Test
    void blocksWhenOnlyABlockRuleMatches() {
        Decision decision = evaluate("https://example.com/forum", rules("example.com"), rules());

        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(decision.matchedRule()).contains(RuleNormalizer.normalize("example.com"));
        assertThat(decision.isBlocked()).isTrue();
    }

    @Test
    void allowsWhenOnlyAnAllowlistRuleMatches() {
        Decision decision = evaluate("https://example.com/docs", rules(), rules("example.com/docs"));

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    // The worked example from the requirements: block example.com, allow example.com/docs.

    @Test
    void moreSpecificAllowlistPathOverridesABroaderDomainBlock() {
        List<UrlRule> block = rules("example.com");
        List<UrlRule> allow = rules("example.com/docs");

        assertThat(evaluate("https://example.com/docs", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
        assertThat(evaluate("https://example.com/docs/setup", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
        assertThat(evaluate("https://example.com/docs?page=2", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
        assertThat(evaluate("https://example.com/forum", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://example.com/", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://example.com/docsfoo", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
    }

    @Test
    void moreSpecificBlockPathOverridesABroaderDomainAllowlist() {
        List<UrlRule> block = rules("youtube.com/shorts");
        List<UrlRule> allow = rules("youtube.com");

        assertThat(evaluate("https://youtube.com/shorts/abc", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://youtube.com/watch", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    @Test
    void allowlistWinsWhenEquallySpecificToABlockRule() {
        Decision decision = evaluate("https://example.com/docs/x", rules("example.com/docs"), rules("example.com/docs"));

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    @Test
    void allowlistWinsTieBetweenEquallySpecificDomainRules() {
        assertThat(evaluate("https://example.com/", rules("example.com"), rules("example.com")).verdict())
                .isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    @Test
    void allowlistedSubdomainOverridesBlockedParentDomain() {
        List<UrlRule> block = rules("reddit.com");
        List<UrlRule> allow = rules("old.reddit.com");

        assertThat(evaluate("https://old.reddit.com/r/programming", block, allow).verdict())
                .isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
        assertThat(evaluate("https://www.reddit.com/r/programming", block, allow).verdict())
                .isEqualTo(Verdict.BLOCKED);
    }

    @Test
    void blockedSubdomainOverridesAllowlistedParentDomain() {
        List<UrlRule> block = rules("news.example.com");
        List<UrlRule> allow = rules("example.com");

        assertThat(evaluate("https://news.example.com/", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://www.example.com/", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    @Test
    void mostSpecificRuleWinsAcrossSeveralNestedRules() {
        // block example.com > allow /docs > block /docs/internal > allow /docs/internal/public
        List<UrlRule> block = rules("example.com", "example.com/docs/internal");
        List<UrlRule> allow = rules("example.com/docs", "example.com/docs/internal/public");

        assertThat(evaluate("https://example.com/forum", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://example.com/docs/setup", block, allow).verdict()).isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
        assertThat(evaluate("https://example.com/docs/internal/wiki", block, allow).verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(evaluate("https://example.com/docs/internal/public/faq", block, allow).verdict())
                .isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
    }

    @Test
    void reportsTheRuleThatDecidedTheOutcome() {
        List<UrlRule> block = rules("example.com", "example.com/ads");
        List<UrlRule> allow = rules("example.com/docs");

        assertThat(evaluate("https://example.com/ads/banner", block, allow).matchedRule())
                .contains(RuleNormalizer.normalize("example.com/ads"));
        assertThat(evaluate("https://example.com/docs", block, allow).matchedRule())
                .contains(RuleNormalizer.normalize("example.com/docs"));
    }

    @Test
    void ruleOrderDoesNotChangeTheOutcome() {
        String url = "https://example.com/docs/internal/wiki";

        Decision forward = evaluate(url, rules("example.com", "example.com/docs/internal"), rules("example.com/docs"));
        Decision reversed = evaluate(url, rules("example.com/docs/internal", "example.com"), rules("example.com/docs"));

        assertThat(forward).isEqualTo(reversed);
        assertThat(forward.verdict()).isEqualTo(Verdict.BLOCKED);
    }
}
