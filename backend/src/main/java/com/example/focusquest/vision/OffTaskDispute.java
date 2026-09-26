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

/** An off-task episode the user marked as inaccurate, known by when it started. It is never subtracted. */
@Entity
@Table(name = "off_task_disputes")
public class OffTaskDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSession session;

    @Column(name = "episode_started_at", nullable = false)
    private Instant episodeStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OffTaskDispute() {
    }

    public OffTaskDispute(FocusSession session, Instant episodeStartedAt, Instant createdAt) {
        this.session = session;
        this.episodeStartedAt = episodeStartedAt;
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
