package com.example.focusquest.auth;

/**
 * Which program holds an extension credential. Each has its own: the browser extension, and the
 * desktop app that keeps blocked Mac apps closed. Issuing one replaces only the same client's.
 */
public enum ExtensionClient {

    /** The browser extension: {@code fqx_} tokens, ROLE_EXTENSION, every {@code /api/extension/**} route. */
    BROWSER("fqx_", "ROLE_EXTENSION"),

    /** The desktop app: {@code fqd_} tokens, ROLE_DESKTOP, which only reads the blocking state (SecurityConfig). */
    DESKTOP("fqd_", "ROLE_DESKTOP");

    private final String tokenPrefix;
    private final String role;

    ExtensionClient(String tokenPrefix, String role) {
        this.tokenPrefix = tokenPrefix;
        this.role = role;
    }

    public String tokenPrefix() {
        return tokenPrefix;
    }

    public String role() {
        return role;
    }

    /** The client a token is for, by its prefix; null if it is not an extension credential at all. */
    public static ExtensionClient ofToken(String token) {
        for (ExtensionClient client : values()) {
            if (token != null && token.startsWith(client.tokenPrefix)) {
                return client;
            }
        }
        return null;
    }
}
