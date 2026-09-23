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
@Table(name = "streak_periods")
public class StreakPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // References the StreakConfiguration this period was created from. Stored as a plain id
    // rather than a relation: the fields below are copied from it at creation time, so this
    // period keeps behaving under its original configuration even if that configuration is
    // later changed or superseded.
    @Column(name = "configuration_snapshot_id", nullable = false)
    private Long configurationSnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private StreakPeriodType periodType;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "target_minutes", nullable = false)
    private int targetMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_task_mode", nullable = false, length = 20)
    private TaskMode requiredTaskMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_category", length = 30)
    private TaskCategory requiredCategory;

    // Tracked in seconds, not minutes, so that many small contributions (short sessions,
    // finalized pauses) accumulate exactly instead of losing time to per-contribution
    // minute rounding.
    @Column(name = "qualifying_seconds", nullable = false)
    private long qualifyingSeconds;

    @Column(name = "overtime_seconds", nullable = false)
    private long overtimeSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StreakPeriodStatus status;

    @Column(name = "freeze_consumed", nullable = false)
    private boolean freezeConsumed;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StreakPeriod() {
    }

    public StreakPeriod(User user, Long configurationSnapshotId, StreakPeriodType periodType,
                         Instant startTime, Instant endTime, int targetMinutes,
                         TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        this.user = user;
        this.configurationSnapshotId = configurationSnapshotId;
        this.periodType = periodType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.targetMinutes = targetMinutes;
        this.requiredTaskMode = requiredTaskMode;
        this.requiredCategory = requiredCategory;
        this.qualifyingSeconds = 0;
        this.overtimeSeconds = 0;
        this.status = StreakPeriodStatus.ACTIVE;
        this.freezeConsumed = false;
    }

    // Domain mutators. Package-private: only StreakService may drive period state, which
    // keeps transition and eligibility rules centralized in the service layer.

    void recordQualifyingSeconds(long seconds) {
        long requiredSeconds = targetMinutes * 60L;
        long roomLeft = Math.max(0, requiredSeconds - qualifyingSeconds);
        long withinTarget = Math.min(seconds, roomLeft);
        this.qualifyingSeconds += withinTarget;
        this.overtimeSeconds += seconds - withinTarget;
    }

    boolean hasReachedTarget() {
        return qualifyingSeconds >= targetMinutes * 60L;
    }

    void markCompleted(Instant now) {
        this.status = StreakPeriodStatus.COMPLETED;
        this.completedAt = now;
    }

    void markMissed() {
        this.status = StreakPeriodStatus.MISSED;
    }

    void markFrozen() {
        this.status = StreakPeriodStatus.FROZEN;
        this.freezeConsumed = true;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getConfigurationSnapshotId() {
        return configurationSnapshotId;
    }

    public StreakPeriodType getPeriodType() {
        return periodType;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
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

    public long getQualifyingSeconds() {
        return qualifyingSeconds;
    }

    public long getOvertimeSeconds() {
        return overtimeSeconds;
    }

    public StreakPeriodStatus getStatus() {
        return status;
    }

    public boolean isFreezeConsumed() {
        return freezeConsumed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
