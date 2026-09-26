package com.example.focusquest.vision;

import com.example.focusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Whether the user has turned camera verification on, recorded as consent to a version of the
 * consent text, and whether new sessions use it by default. At most one per user.
 */
@Entity
@Table(name = "camera_settings")
public class CameraSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "consent_version")
    private Integer consentVersion;

    @Column(name = "consented_at")
    private Instant consentedAt;

    @Column(name = "verify_new_sessions", nullable = false)
    private boolean verifyNewSessions;

    protected CameraSettings() {
    }

    public CameraSettings(User user, Integer consentVersion, Instant consentedAt, boolean verifyNewSessions) {
        this.user = user;
        this.consentVersion = consentVersion;
        this.consentedAt = consentedAt;
        this.verifyNewSessions = verifyNewSessions;
    }

    void giveConsent(int version, Instant now) {
        this.consentVersion = version;
        this.consentedAt = now;
    }

    void withdrawConsent() {
        this.consentVersion = null;
        this.consentedAt = null;
    }

    void setVerifyNewSessions(boolean verifyNewSessions) {
        this.verifyNewSessions = verifyNewSessions;
    }

    /** On only with consent to the given version of the consent text, the current one. */
    boolean isEnabled(int currentConsentVersion) {
        return consentVersion != null && consentVersion == currentConsentVersion;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Integer getConsentVersion() {
        return consentVersion;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public boolean isVerifyNewSessions() {
        return verifyNewSessions;
    }
}
