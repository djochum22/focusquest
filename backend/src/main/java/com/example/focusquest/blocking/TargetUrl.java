package com.example.focusquest.blocking;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * The parts of a visited URL that rule matching looks at, in the same normalized form as
 * {@link UrlRule}. Query strings and fragments are dropped.
 *
 * <p>Normalization errs toward blocking: hosts and paths are lowercased, percent-escapes are
 * decoded, dot segments and repeated slashes are collapsed, and a trailing slash is ignored, so a
 * URL cannot dodge a rule by being spelled differently.
 *
 * @param host normalized host without a trailing dot
 * @param path normalized path without a trailing slash; empty for the site root
 */
public record TargetUrl(String host, String path) {

    /**
     * Parses an http(s) URL. Returns empty for anything else (other schemes, no host, or text that
     * is not a URL), since the extension only ever blocks web pages.
     */
    public static Optional<TargetUrl> parse(String url) {
        if (url == null) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url.trim()).normalize();
        } catch (Exception e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        path = path.replaceAll("/{2,}", "/");
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return Optional.of(new TargetUrl(host, path));
    }
}
