package com.example.focusquest.auth;

import com.example.focusquest.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and checks the credential the Chrome extension signs in with.
 *
 * <p>It is an opaque random token rather than a JWT for three reasons. It can be revoked, by
 * deleting its row. It does not depend on the JWT signing secret, so restarting the backend does not
 * sign the extension out. And it is only accepted on {@code /api/extension/**} (see
 * {@code SecurityConfig}), so a leaked copy cannot start sessions, read history or delete data.
 * A user has at most one: issuing a new one replaces the old.
 */
@Service
public class ExtensionCredentialService {

    /** Lets the authentication filter tell an extension token from a JWT without a database lookup. */
    public static final String TOKEN_PREFIX = "fqx_";

    private static final int TOKEN_BYTES = 32;

    private final ExtensionCredentialRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ExtensionCredentialService(ExtensionCredentialRepository repository) {
        this.repository = repository;
    }

    /** Replaces any existing credential and returns the new one. The token is not recoverable later. */
    @Transactional
    public IssuedExtensionCredential issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.deleteAllByUser(user);
        ExtensionCredential credential = repository.save(new ExtensionCredential(user, hash(token)));
        return new IssuedExtensionCredential(token, credential.getCreatedAt());
    }

    @Transactional
    public void revoke(User user) {
        repository.deleteAllByUser(user);
    }

    /** The username a token belongs to, or empty if it is not a live extension token. */
    @Transactional(readOnly = true)
    public Optional<String> findUsername(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(token)).map(credential -> credential.getUser().getUsername());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
