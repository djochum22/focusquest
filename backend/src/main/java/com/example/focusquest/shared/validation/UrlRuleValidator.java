package com.example.focusquest.shared.validation;

import java.util.regex.Pattern;

/**
 * Validates the raw string form of a blocking or allowlist rule: either a bare domain
 * ("youtube.com") or a domain followed by a URL path ("youtube.com/shorts"). Matching
 * behavior for subdomains, descendant paths, and allowlist precedence is the Chrome
 * extension's responsibility; this class only checks that a rule is well-formed enough
 * to be stored and later matched against.
 */
public final class UrlRuleValidator {

    private static final int MAX_RULE_LENGTH = 253;
    private static final Pattern DOMAIN_LABEL = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");

    private UrlRuleValidator() {
    }

    /**
     * Returns true when {@code value} is a valid domain rule, optionally followed by a
     * valid URL path.
     */
    public static boolean isValidUrlRule(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (!trimmed.equals(value) || trimmed.contains("://") || containsWhitespace(trimmed)
                || trimmed.length() > MAX_RULE_LENGTH) {
            return false;
        }

        int slashIndex = trimmed.indexOf('/');
        String domainPart = slashIndex == -1 ? trimmed : trimmed.substring(0, slashIndex);
        if (!isValidDomain(domainPart)) {
            return false;
        }

        return slashIndex == -1 || isValidPath(trimmed.substring(slashIndex));
    }

    /** Returns true when {@code domain} is a well-formed hostname with at least two labels. */
    public static boolean isValidDomain(String domain) {
        if (domain == null || domain.isBlank() || domain.length() > MAX_RULE_LENGTH) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    /** Returns true when {@code path} starts with "/", has no empty segments, and is non-root. */
    public static boolean isValidPath(String path) {
        if (path == null || !path.startsWith("/") || path.equals("/") || path.contains("//")) {
            return false;
        }
        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
