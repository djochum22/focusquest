package com.example.focusquest.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;

@Service
public class UserService {

    /** BCrypt only reads this many bytes of a password and the encoder refuses anything longer. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean hasUser() {
        return userRepository.count() > 0;
    }

    public User createUser(String username, String rawPassword, String displayName, String timezone) {
        if (exceedsPasswordLimit(rawPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes long");
        }
        // Every streak calculation resolves this zone, so an unknown one would fail all of them later.
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone");
        }
        if (hasUser()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A local user account already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        User user = new User(username, passwordEncoder.encode(rawPassword), displayName, timezone);
        return userRepository.save(user);
    }

    /** True when the password is longer than the password encoder can handle, so it can never match. */
    public static boolean exceedsPasswordLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
