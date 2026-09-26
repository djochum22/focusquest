package com.example.focusquest.vision;

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

/**
 * Off-task time settled against a session: the part of an episode's subtraction that fell in one
 * credited stretch of active time, [{@code deductionStartedAt}, {@code deductionEndedAt}).
 */
@Entity
@Table(name = "off_task_intervals")
public class OffTaskInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSession session;

    @Column(name = "episode_started_at", nullable = false)
    private Instant episodeStartedAt;

    @Column(name = "warned_at", nullable = false)
    private Instant warnedAt;

    @Column(name = "deduction_started_at", nullable = false)
    private Instant deductionStartedAt;

    @Column(name = "deduction_ended_at", nullable = false)
    private Instant deductionEndedAt;

    @Column(name = "deducted_seconds", nullable = false)
    private long deductedSeconds;

    @Column(name = "disputed", nullable = false)
    private boolean disputed;

    protected OffTaskInterval() {
    }

    public OffTaskInterval(FocusSession session, Instant episodeStartedAt, Instant warnedAt,
                           Instant deductionStartedAt, Instant deductionEndedAt, long deductedSeconds,
                           boolean disputed) {
        this.session = session;
        this.episodeStartedAt = episodeStartedAt;
        this.warnedAt = warnedAt;
        this.deductionStartedAt = deductionStartedAt;
        this.deductionEndedAt = deductionEndedAt;
        this.deductedSeconds = deductedSeconds;
        this.disputed = disputed;
    }

    void markDisputed() {
        this.disputed = true;
    }

    public Long getId() {
        return id;
    }

    public FocusSession getSession() {
        return session;
    }

    public Instant getEpisodeStartedAt() {
        return episodeStartedAt;
    }

    public Instant getWarnedAt() {
        return warnedAt;
    }

    public Instant getDeductionStartedAt() {
        return deductionStartedAt;
    }

    public Instant getDeductionEndedAt() {
        return deductionEndedAt;
    }

    public long getDeductedSeconds() {
        return deductedSeconds;
    }

    public boolean isDisputed() {
        return disputed;
    }
}
