package com.example.focusquest.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "session_pauses")
public class SessionPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSession session;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "finalized", nullable = false)
    private boolean finalized;

    protected SessionPause() {
    }

    public SessionPause(FocusSession session, Instant startedAt) {
        this.session = session;
        this.startedAt = startedAt;
        this.finalized = false;
    }

    void finalizePause(Instant endedAt) {
        this.endedAt = endedAt;
        this.durationSeconds = Duration.between(startedAt, endedAt).getSeconds();
        this.finalized = true;
    }

    public Long getId() {
        return id;
    }

    public FocusSession getSession() {
        return session;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isFinalized() {
        return finalized;
    }
}
