package com.example.focusquest.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Bridges the {@link ValidUrlRule} annotation to {@link UrlRuleValidator}. */
public class UrlRuleConstraintValidator implements ConstraintValidator<ValidUrlRule, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return UrlRuleValidator.isValidUrlRule(value);
    }
}
