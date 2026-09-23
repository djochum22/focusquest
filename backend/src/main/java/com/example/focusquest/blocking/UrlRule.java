package com.example.focusquest.blocking;

/**
 * A normalized blocking or allowlist rule: a host and an optional path prefix.
 *
 * <p>Matching semantics (mirrored by the Chrome extension's {@code urlMatcher.ts}):
 * <ul>
 *   <li>The host matches when it equals the rule host or is a subdomain of it, so
 *       {@code youtube.com} covers {@code www.youtube.com} but not {@code notyoutube.com}.</li>
 *   <li>An empty path matches every path on the host. A non-empty path matches itself and all
 *       descendant paths on a segment boundary, so {@code /shorts} covers {@code /shorts} and
 *       {@code /shorts/abc} but not {@code /shortsfoo}.</li>
 *   <li>Query strings and fragments never take part in matching.</li>
 * </ul>
 *
 * @param host normalized (lowercase) host, e.g. {@code youtube.com}
 * @param path normalized (lowercase, no trailing slash) path prefix, or an empty string for a domain rule
 */
public record UrlRule(String host, String path) implements Comparable<UrlRule> {

    /** Parses the canonical string form produced by {@link #value()}. */
    public static UrlRule parse(String value) {
        int slashIndex = value.indexOf('/');
        return slashIndex == -1
                ? new UrlRule(value, "")
                : new UrlRule(value.substring(0, slashIndex), value.substring(slashIndex));
    }

    public TargetType type() {
        return path.isEmpty() ? TargetType.DOMAIN : TargetType.URL_PATH;
    }

    /** The canonical string form, e.g. {@code youtube.com} or {@code youtube.com/shorts}. */
    public String value() {
        return host + path;
    }

    public boolean matches(TargetUrl url) {
        return matchesHost(url.host()) && matchesPath(url.path());
    }

    /**
     * Orders rules by specificity: more host labels first ({@code m.youtube.com} beats
     * {@code youtube.com}), then more path segments ({@code /shorts/a} beats {@code /shorts}).
     * A rule that is more specific compares as greater.
     */
    @Override
    public int compareTo(UrlRule other) {
        int byHost = Integer.compare(hostLabelCount(), other.hostLabelCount());
        return byHost != 0 ? byHost : Integer.compare(pathSegmentCount(), other.pathSegmentCount());
    }

    private boolean matchesHost(String urlHost) {
        return urlHost.equals(host) || urlHost.endsWith("." + host);
    }

    private boolean matchesPath(String urlPath) {
        return path.isEmpty() || urlPath.equals(path) || urlPath.startsWith(path + "/");
    }

    private int hostLabelCount() {
        return (int) host.chars().filter(c -> c == '.').count() + 1;
    }

    private int pathSegmentCount() {
        return (int) path.chars().filter(c -> c == '/').count();
    }
}
