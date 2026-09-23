package com.example.focusquest.blocking;

import com.example.focusquest.shared.validation.UrlRuleValidator;

import java.util.Locale;

/**
 * Turns a user-entered rule into its canonical {@link UrlRule}, so that equivalent spellings are
 * stored, compared and sent to the extension identically (mirrored by the extension's
 * {@code ruleNormalizer.ts}).
 *
 * <p>Rules are lowercased and a trailing path slash is dropped. Path matching is deliberately
 * case-insensitive: a blocker that a change of letter case can slip past is not doing its job, and
 * Chrome's own declarative rules also match case-insensitively by default. A leading {@code www.}
 * is kept, because {@code www.example.com} and {@code example.com} are different rules with
 * different specificity.
 */
public final class RuleNormalizer {

    private RuleNormalizer() {
    }

    /** @throws IllegalArgumentException when {@code raw} is not a valid domain or domain/path rule */
    public static UrlRule normalize(String raw) {
        if (!UrlRuleValidator.isValidUrlRule(raw)) {
            throw new IllegalArgumentException(
                    "must be a valid domain (e.g. example.com) or domain/path rule (e.g. example.com/path)");
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        int slashIndex = lowered.indexOf('/');
        if (slashIndex == -1) {
            return new UrlRule(lowered, "");
        }
        String path = lowered.substring(slashIndex);
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return new UrlRule(lowered.substring(0, slashIndex), path);
    }
}
