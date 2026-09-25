package com.example.focusquest.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // When the Chrome extension last checked in for this user, session or not.
    @Column(name = "last_extension_heartbeat_at")
    private Instant lastExtensionHeartbeatAt;

    protected User() {
    }

    public User(String username, String passwordHash, String displayName, String timezone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timezone = timezone;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastExtensionHeartbeatAt() {
        return lastExtensionHeartbeatAt;
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void recordExtensionHeartbeat(Instant now) {
        this.lastExtensionHeartbeatAt = now;
    }
}
