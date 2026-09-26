package com.example.focusquest.vision;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and checks the token the camera companion program signs in with, and remembers when it last
 * reported. Like the extension's token it is opaque, revocable by deleting its row, and accepted only
 * on its own endpoints ({@code /api/companion/**}, see {@code SecurityConfig}). A user has at most one.
 */
@Service
public class CompanionCredentialService {

    /** Lets the authentication filter tell a companion token from a JWT without a database lookup. */
    public static final String TOKEN_PREFIX = "fqc_";

    /** The program reports every few seconds; one not heard from for this long is taken to be gone. */
    public static final Duration CONNECTED_WITHIN = Duration.ofSeconds(30);

    private static final int TOKEN_BYTES = 32;

    private final CompanionCredentialRepository repository;
    private final ClockProvider clockProvider;
    private final SecureRandom random = new SecureRandom();

    public CompanionCredentialService(CompanionCredentialRepository repository, ClockProvider clockProvider) {
        this.repository = repository;
        this.clockProvider = clockProvider;
    }

    /** Replaces any existing credential and returns the new token. It cannot be recovered later. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.deleteAllByUser(user);
        repository.save(new CompanionCredential(user, hash(token), clockProvider.now()));
        return token;
    }

    @Transactional
    public void revoke(User user) {
        repository.deleteAllByUser(user);
    }

    /** The username a token belongs to, or empty if it is not a live companion token. */
    @Transactional(readOnly = true)
    public Optional<String> findUsername(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(token)).map(credential -> credential.getUser().getUsername());
    }

    /** Records that the user's companion program just reported. */
    @Transactional
    public void recordSeen(User user) {
        repository.findByUser(user).ifPresent(credential -> {
            credential.recordSeen(clockProvider.now());
            repository.save(credential);
        });
    }

    @Transactional(readOnly = true)
    public Optional<CompanionCredential> find(User user) {
        return repository.findByUser(user);
    }

    /** Whether the user's companion program reported within {@link #CONNECTED_WITHIN}. */
    @Transactional(readOnly = true)
    public boolean isConnected(User user) {
        Instant now = clockProvider.now();
        return repository.findByUser(user)
                .map(CompanionCredential::getLastSeenAt)
                .map(lastSeen -> !lastSeen.isBefore(now.minus(CONNECTED_WITHIN)))
                .orElse(false);
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
