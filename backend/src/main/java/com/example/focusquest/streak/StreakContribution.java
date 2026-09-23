package com.example.focusquest.streak;

import com.example.focusquest.session.FocusSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "streak_contributions")
public class StreakContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "streak_period_id", nullable = false)
    private StreakPeriod streakPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSession session;

    @Column(name = "active_seconds", nullable = false)
    private long activeSeconds;

    @Column(name = "paused_seconds", nullable = false)
    private long pausedSeconds;

    @Column(name = "qualifying_seconds", nullable = false)
    private long qualifyingSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StreakContribution() {
    }

    public StreakContribution(StreakPeriod streakPeriod, FocusSession session, long activeSeconds,
                               long pausedSeconds, Instant createdAt) {
        this.streakPeriod = streakPeriod;
        this.session = session;
        this.activeSeconds = activeSeconds;
        this.pausedSeconds = pausedSeconds;
        this.qualifyingSeconds = activeSeconds + pausedSeconds;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public StreakPeriod getStreakPeriod() {
        return streakPeriod;
    }

    public FocusSession getSession() {
        return session;
    }

    public long getActiveSeconds() {
        return activeSeconds;
    }

    public long getPausedSeconds() {
        return pausedSeconds;
    }

    public long getQualifyingSeconds() {
        return qualifyingSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
