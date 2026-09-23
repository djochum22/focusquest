package com.example.focusquest.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves {@link ValidUrlRule} is wired to {@link UrlRuleConstraintValidator} end to end. */
class ValidUrlRuleAnnotationTest {

    private record RuleHolder(@ValidUrlRule String value) {
    }

    @Test
    void annotationAcceptsValidRule() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<RuleHolder>> violations =
                    validator.validate(new RuleHolder("youtube.com/shorts"));
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void annotationRejectsInvalidRule() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<RuleHolder>> violations =
                    validator.validate(new RuleHolder("http://youtube.com"));
            assertThat(violations).hasSize(1);
        }
    }
}
