package com.example.focusquest.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlRuleValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtube.com/shorts",
            "youtube.com/shorts/example",
            "sub.domain.example.co.uk/a/b-c_d"
    })
    void acceptsValidDomainAndPathRules(String value) {
        assertThat(UrlRuleValidator.isValidUrlRule(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "http://youtube.com",
            "https://youtube.com/shorts",
            "localhost",
            "youtube.com/",
            "youtube.com//shorts",
            "you tube.com",
            "-youtube.com",
            "youtube.com/short s",
            " youtube.com"
    })
    void rejectsInvalidRules(String value) {
        assertThat(UrlRuleValidator.isValidUrlRule(value)).isFalse();
    }

    @Test
    void rejectsNullValue() {
        assertThat(UrlRuleValidator.isValidUrlRule(null)).isFalse();
    }

    @Test
    void isValidDomainRejectsSingleLabelHost() {
        assertThat(UrlRuleValidator.isValidDomain("localhost")).isFalse();
        assertThat(UrlRuleValidator.isValidDomain("youtube.com")).isTrue();
    }

    @Test
    void isValidPathRejectsRootAndDoubleSlashes() {
        assertThat(UrlRuleValidator.isValidPath("/")).isFalse();
        assertThat(UrlRuleValidator.isValidPath("//shorts")).isFalse();
        assertThat(UrlRuleValidator.isValidPath("/shorts")).isTrue();
    }
}
