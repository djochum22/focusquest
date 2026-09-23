package com.example.focusquest.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field or parameter as a blocking/allowlist rule: a bare domain
 * ("example.com") or a domain plus path ("example.com/path"). Backed by
 * {@link UrlRuleValidator#isValidUrlRule(String)}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UrlRuleConstraintValidator.class)
public @interface ValidUrlRule {

    String message() default "must be a valid domain (e.g. example.com) or domain/path rule (e.g. example.com/path)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
