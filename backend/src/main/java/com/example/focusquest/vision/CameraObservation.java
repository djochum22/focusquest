package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
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
 * One continuous stretch of one off-task signal, as the companion program saw it. It is sent again
 * with the same {@code clientEventId} to extend it while it lasts. Only what was observed is kept.
 */
@Entity
@Table(name = "camera_observations")
public class CameraObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSession session;

    @Column(name = "client_event_id", nullable = false, length = 64)
    private String clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 20)
    private OffTaskSignal signal;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "observed_until", nullable = false)
    private Instant observedUntil;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected CameraObservation() {
    }

    public CameraObservation(FocusSession session, String clientEventId, OffTaskSignal signal, double confidence,
                             Instant startedAt, Instant observedUntil, Instant receivedAt) {
        this.session = session;
        this.clientEventId = clientEventId;
        this.signal = signal;
        this.confidence = confidence;
        this.startedAt = startedAt;
        this.observedUntil = observedUntil;
        this.receivedAt = receivedAt;
    }

    /** A later report of the same stretch: it can only grow, and its confidence is the latest one. */
    void extend(Instant observedUntil, double confidence, Instant receivedAt) {
        if (observedUntil.isAfter(this.observedUntil)) {
            this.observedUntil = observedUntil;
        }
        this.confidence = confidence;
        this.receivedAt = receivedAt;
    }

    OffTaskCalculator.Observed toObserved() {
        return new OffTaskCalculator.Observed(signal, confidence, startedAt, observedUntil);
    }

    public Long getId() {
        return id;
    }

    public FocusSession getSession() {
        return session;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public OffTaskSignal getSignal() {
        return signal;
    }

    public double getConfidence() {
        return confidence;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getObservedUntil() {
        return observedUntil;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
