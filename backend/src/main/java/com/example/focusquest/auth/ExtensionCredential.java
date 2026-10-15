package com.example.focusquest.auth;

import com.example.focusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The stored side of an extension token: who it belongs to, which program holds it, and a hash of it,
 * never the token. One per user and client.
 */
@Entity
@Table(name = "extension_credentials")
public class ExtensionCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ExtensionClient client;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExtensionCredential() {
    }

    public ExtensionCredential(User user, ExtensionClient client, String tokenHash) {
        this.user = user;
        this.client = client;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public ExtensionClient getClient() {
        return client;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
