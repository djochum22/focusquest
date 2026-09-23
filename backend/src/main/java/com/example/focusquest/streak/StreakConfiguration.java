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

    /**
     * The default streak: a daily target of 30 minutes of task-based work (any category). Every
     * user always has at least this daily configuration; see {@link StreakService}.
     */
    public static final StreakPeriodType DEFAULT_PERIOD_TYPE = StreakPeriodType.DAILY;
    public static final int DEFAULT_TARGET_MINUTES = 30;
    public static final TaskMode DEFAULT_TASK_MODE = TaskMode.TASK_REQUIRED;

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

    /**
     * The default daily configuration. It is effective from the epoch so that it governs every
     * period the user can ever be in, including any that started before the row was created.
     */
    public static StreakConfiguration defaultFor(User user, Instant createdAt) {
        return new StreakConfiguration(user, DEFAULT_PERIOD_TYPE, DEFAULT_TARGET_MINUTES, DEFAULT_TASK_MODE,
                null, Instant.EPOCH, createdAt);
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
