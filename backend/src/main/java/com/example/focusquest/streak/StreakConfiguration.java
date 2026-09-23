package com.example.focusquest.streak;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
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

@Entity
@Table(name = "streak_configurations")
public class StreakConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private StreakPeriodType periodType;

    @Column(name = "target_minutes", nullable = false)
    private int targetMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_task_mode", nullable = false, length = 20)
    private TaskMode requiredTaskMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_category", length = 30)
    private TaskCategory requiredCategory;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StreakConfiguration() {
    }

    public StreakConfiguration(User user, StreakPeriodType periodType, int targetMinutes,
                                TaskMode requiredTaskMode, TaskCategory requiredCategory,
                                Instant effectiveFrom, Instant createdAt) {
        this.user = user;
        this.periodType = periodType;
        this.targetMinutes = targetMinutes;
        this.requiredTaskMode = requiredTaskMode;
        this.requiredCategory = requiredCategory;
        this.effectiveFrom = effectiveFrom;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public StreakPeriodType getPeriodType() {
        return periodType;
    }

    public int getTargetMinutes() {
        return targetMinutes;
    }

    public TaskMode getRequiredTaskMode() {
        return requiredTaskMode;
    }

    public TaskCategory getRequiredCategory() {
        return requiredCategory;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
