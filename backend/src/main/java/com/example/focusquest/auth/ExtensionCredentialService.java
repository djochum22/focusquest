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
 * Issues and checks the credentials the browser extension and the desktop app sign in with
 * ({@link ExtensionClient}).
 *
 * <p>It is an opaque random token rather than a JWT for three reasons. It can be revoked, by
 * deleting its row. It does not depend on the JWT signing secret, so restarting the backend does not
 * sign the extension out. And it is only accepted on {@code /api/extension/**} (see
 * {@code SecurityConfig}), so a leaked copy cannot start sessions, read history or delete data; a
 * desktop token only reads the blocking state. A user has at most one per client: issuing a new one
 * replaces the old one of that client only.
 */
@Service
public class ExtensionCredentialService {

    private static final int TOKEN_BYTES = 32;

    private final ExtensionCredentialRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ExtensionCredentialService(ExtensionCredentialRepository repository) {
        this.repository = repository;
    }

    /**
     * Replaces the client's existing credential, if any, and returns the new one. The token is not
     * recoverable later.
     */
    @Transactional
    public IssuedExtensionCredential issue(User user, ExtensionClient client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = client.tokenPrefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.deleteAllByUserAndClient(user, client);
        ExtensionCredential credential = repository.save(new ExtensionCredential(user, client, hash(token)));
        return new IssuedExtensionCredential(token, credential.getCreatedAt());
    }

    @Transactional
    public void revoke(User user, ExtensionClient client) {
        repository.deleteAllByUserAndClient(user, client);
    }

    /**
     * The username a token belongs to, or empty if it is not a live token of the client its prefix
     * names.
     */
    @Transactional(readOnly = true)
    public Optional<String> findUsername(String token) {
        ExtensionClient client = ExtensionClient.ofToken(token);
        if (client == null) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(token))
                .filter(credential -> credential.getClient() == client)
                .map(credential -> credential.getUser().getUsername());
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
