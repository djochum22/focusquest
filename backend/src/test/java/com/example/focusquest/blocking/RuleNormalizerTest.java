package com.example.focusquest.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RuleNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "youtube.com,               youtube.com",
            "YouTube.COM,               youtube.com",
            "www.YouTube.com,           www.youtube.com",
            "youtube.com/shorts,        youtube.com/shorts",
            "YouTube.com/Shorts,        youtube.com/shorts",
            "youtube.com/shorts/,       youtube.com/shorts",
            "reddit.com/r/all,          reddit.com/r/all"
    })
    void normalizesToCanonicalLowercaseForm(String raw, String expected) {
        assertThat(RuleNormalizer.normalize(raw).value()).isEqualTo(expected);
    }

    @Test
    void domainRuleHasNoPathAndIsTypedAsDomain() {
        UrlRule rule = RuleNormalizer.normalize("youtube.com");

        assertThat(rule.host()).isEqualTo("youtube.com");
        assertThat(rule.path()).isEmpty();
        assertThat(rule.type()).isEqualTo(TargetType.DOMAIN);
    }

    @Test
    void pathRuleIsSplitIntoHostAndPathAndTypedAsUrlPath() {
        UrlRule rule = RuleNormalizer.normalize("youtube.com/shorts/abc");

        assertThat(rule.host()).isEqualTo("youtube.com");
        assertThat(rule.path()).isEqualTo("/shorts/abc");
        assertThat(rule.type()).isEqualTo(TargetType.URL_PATH);
    }

    @Test
    void equivalentSpellingsNormalizeToEqualRules() {
        assertThat(RuleNormalizer.normalize("YouTube.com/Shorts/")).isEqualTo(RuleNormalizer.normalize("youtube.com/shorts"));
    }

    @Test
    void valueRoundTripsThroughParse() {
        UrlRule rule = RuleNormalizer.normalize("reddit.com/r/all");

        assertThat(UrlRule.parse(rule.value())).isEqualTo(rule);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "https://youtube.com",
            "localhost",
            "youtube.com/",
            "youtube.com//shorts",
            "youtube.com/watch?v=abc",
            "youtube.com/shorts#top",
            "youtube.com/a/../b",
            " youtube.com"
    })
    void rejectsInvalidRules(String raw) {
        assertThatThrownBy(() -> RuleNormalizer.normalize(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> RuleNormalizer.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
