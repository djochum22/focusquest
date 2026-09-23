package com.example.focusquest.blocking;

/**
 * A rule in the form the extension consumes: the canonical value plus its already-split host and
 * path, so the extension does not have to re-parse it.
 *
 * @param path path prefix, or null for a domain rule
 */
public record ExtensionRule(
        Long id,
        TargetType targetType,
        String targetValue,
        String host,
        String path,
        String displayName
) {

    public static ExtensionRule from(RuleTarget target) {
        UrlRule rule = target.rule();
        return new ExtensionRule(target.getId(), target.getTargetType(), target.getTargetValue(),
                rule.host(), rule.path().isEmpty() ? null : rule.path(), target.getDisplayName());
    }
}
