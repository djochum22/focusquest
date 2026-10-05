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

/** The stored side of a companion program token: whose it is and a hash of it, never the token. */
@Entity
@Table(name = "companion_credentials")
public class CompanionCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "calibration_requested_at")
    private Instant calibrationRequestedAt;

    @Column(name = "calibration_started_at")
    private Instant calibrationStartedAt;

    @Column(name = "calibrated_at")
    private Instant calibratedAt;

    @Column(name = "calibration_failure", length = 300)
    private String calibrationFailure;

    @Column(name = "camera_problem", length = 300)
    private String cameraProblem;

    protected CompanionCredential() {
    }

    public CompanionCredential(User user, String tokenHash, Instant createdAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    void recordSeen(Instant now) {
        this.lastSeenAt = now;
    }

    /** Why the program cannot use the camera, or null if it can (or is not asked to). */
    void reportCameraProblem(String cameraProblem) {
        this.cameraProblem = cameraProblem == null || cameraProblem.isBlank() ? null : cameraProblem;
    }

    /** The user asked for a calibration; any earlier outcome is forgotten. */
    void requestCalibration(Instant now) {
        calibrationRequestedAt = now;
        calibrationStartedAt = null;
        calibrationFailure = null;
    }

    /** The program began a calibration that was asked for. */
    void calibrationStarted(Instant now) {
        if (calibrationRequestedAt != null) {
            calibrationStartedAt = now;
        }
    }

    /** The program has a calibration from {@code at}: just made, or one it already had. */
    void calibrated(Instant at) {
        calibratedAt = at;
        calibrationRequestedAt = null;
        calibrationStartedAt = null;
        calibrationFailure = null;
    }

    void calibrationFailed(String failure) {
        calibrationFailure = failure;
        calibrationRequestedAt = null;
        calibrationStartedAt = null;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCalibrationRequestedAt() {
        return calibrationRequestedAt;
    }

    public Instant getCalibrationStartedAt() {
        return calibrationStartedAt;
    }

    public Instant getCalibratedAt() {
        return calibratedAt;
    }

    public String getCalibrationFailure() {
        return calibrationFailure;
    }

    public String getCameraProblem() {
        return cameraProblem;
    }
}
