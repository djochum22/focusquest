package com.example.focusquest.session;

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

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "focus_sessions")
public class FocusSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "task_description", length = 500)
    private String taskDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_mode", nullable = false, length = 20)
    private TaskMode taskMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_category", nullable = false, length = 30)
    private TaskCategory taskCategory;

    @Column(name = "planned_focus_minutes", nullable = false)
    private int plannedFocusMinutes;

    @Column(name = "active_focus_seconds", nullable = false)
    private long activeFocusSeconds;

    @Column(name = "finalized_paused_seconds", nullable = false)
    private long finalizedPausedSeconds;

    @Column(name = "qualifying_seconds", nullable = false)
    private long qualifyingSeconds;

    @Column(name = "overtime_seconds", nullable = false)
    private long overtimeSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocking_state", length = 20)
    private BlockingState blockingState;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    @Column(name = "override_used", nullable = false)
    private boolean overrideUsed;

    @Column(name = "completion_xp_awarded", nullable = false)
    private boolean completionXpAwarded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Internal bookkeeping only: when the current uninterrupted ACTIVE segment began, so
    // SessionService can add elapsed seconds to activeFocusSeconds across pause/resume cycles.
    @Column(name = "active_segment_started_at")
    private Instant activeSegmentStartedAt;

    // Internal bookkeeping only: the part of the totals above that SessionService has already
    // credited to the streak, so each credit covers just the time since the previous one.
    @Column(name = "streak_credited_active_seconds", nullable = false)
    private long streakCreditedActiveSeconds;

    @Column(name = "streak_credited_paused_seconds", nullable = false)
    private long streakCreditedPausedSeconds;

    // Internal bookkeeping only: when the Chrome extension last checked in while this session was
    // running (set at start or resume when the extension is alive). Null leaves interruption
    // detection disarmed until the extension checks in.
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    // Whether the camera checks this session (requirements specification, section 21), chosen when
    // it is created.
    @Column(name = "camera_verification", nullable = false)
    private boolean cameraVerification;

    // Off-task time settled against the session: subtracted from its active time in the qualifying
    // time and toward completion. Settled when time is credited to the streak; see SessionService.
    @Column(name = "off_task_seconds", nullable = false)
    private long offTaskSeconds;

    protected FocusSession() {
    }

    public FocusSession(User user, String taskDescription, TaskMode taskMode, TaskCategory taskCategory,
                         int plannedFocusMinutes, Instant createdAt) {
        this(user, taskDescription, taskMode, taskCategory, plannedFocusMinutes, false, createdAt);
    }

    public FocusSession(User user, String taskDescription, TaskMode taskMode, TaskCategory taskCategory,
                         int plannedFocusMinutes, boolean cameraVerification, Instant createdAt) {
        this.cameraVerification = cameraVerification;
        this.user = user;
        this.taskDescription = taskDescription;
        this.taskMode = taskMode;
        this.taskCategory = taskCategory;
        this.plannedFocusMinutes = plannedFocusMinutes;
        this.status = SessionStatus.PLANNED;
        this.activeFocusSeconds = 0;
        this.finalizedPausedSeconds = 0;
        this.qualifyingSeconds = 0;
        this.overtimeSeconds = 0;
        this.overrideUsed = false;
        this.completionXpAwarded = false;
        this.createdAt = createdAt;
    }

    /**
     * Rebuilds an ended or planned session from a backup, exactly as it was recorded. Only restoring
     * a data export uses it; every other session is built by the constructor and moved on by
     * SessionService. A running session cannot be rebuilt: the restore turns it into an interrupted one.
     */
    public static FocusSession restore(User user, String taskDescription, TaskMode taskMode,
                                       TaskCategory taskCategory, int plannedFocusMinutes,
                                       long activeFocusSeconds, long finalizedPausedSeconds, long overtimeSeconds,
                                       SessionStatus status, BlockingState blockingState, Instant startedAt,
                                       Instant completedAt, Instant abandonedAt, boolean overrideUsed,
                                       boolean completionXpAwarded, Instant createdAt,
                                       long streakCreditedActiveSeconds, long streakCreditedPausedSeconds,
                                       boolean cameraVerification, long offTaskSeconds) {
        if (status == SessionStatus.ACTIVE || status == SessionStatus.PAUSED) {
            throw new IllegalArgumentException("A running session cannot be restored as running");
        }
        FocusSession session = new FocusSession(user, taskDescription, taskMode, taskCategory,
                plannedFocusMinutes, cameraVerification, createdAt);
        session.activeFocusSeconds = activeFocusSeconds;
        session.finalizedPausedSeconds = finalizedPausedSeconds;
        session.offTaskSeconds = offTaskSeconds;
        session.recalculateQualifyingSeconds();
        session.overtimeSeconds = overtimeSeconds;
        session.status = status;
        session.blockingState = blockingState;
        session.startedAt = startedAt;
        session.completedAt = completedAt;
        session.abandonedAt = abandonedAt;
        session.overrideUsed = overrideUsed;
        session.completionXpAwarded = completionXpAwarded;
        session.streakCreditedActiveSeconds = streakCreditedActiveSeconds;
        session.streakCreditedPausedSeconds = streakCreditedPausedSeconds;
        return session;
    }

    // Domain mutators. Package-private: only SessionService may drive state transitions,
    // which keeps transition validation centralized in the service layer.

    void begin(Instant now) {
        this.status = SessionStatus.ACTIVE;
        this.startedAt = now;
        this.blockingState = BlockingState.ACTIVE;
        this.activeSegmentStartedAt = now;
    }

    void enterPause() {
        this.status = SessionStatus.PAUSED;
        this.activeSegmentStartedAt = null;
    }

    void resumeFromPause(Instant now) {
        this.status = SessionStatus.ACTIVE;
        this.activeSegmentStartedAt = now;
    }

    void addActiveSeconds(long seconds) {
        this.activeFocusSeconds += seconds;
        recalculateQualifyingSeconds();
    }

    void addFinalizedPausedSeconds(long seconds) {
        this.finalizedPausedSeconds += seconds;
        recalculateQualifyingSeconds();
    }

    /** Off-task time just settled against the session. */
    void addOffTaskSeconds(long seconds) {
        this.offTaskSeconds += seconds;
        recalculateQualifyingSeconds();
    }

    /** Settled off-task time given back after the user disputed it. */
    void restoreOffTaskSeconds(long seconds) {
        this.offTaskSeconds = Math.max(0, this.offTaskSeconds - seconds);
        recalculateQualifyingSeconds();
        if (status == SessionStatus.COMPLETED) {
            this.overtimeSeconds = netOvertimeSeconds();
        }
    }

    private void recalculateQualifyingSeconds() {
        this.qualifyingSeconds = this.activeFocusSeconds + this.finalizedPausedSeconds - this.offTaskSeconds;
    }

    private long netOvertimeSeconds() {
        return Math.max(0, this.activeFocusSeconds - this.offTaskSeconds - this.plannedFocusMinutes * 60L);
    }

    void markCompleted(Instant now) {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = now;
        this.activeSegmentStartedAt = null;
        this.overtimeSeconds = netOvertimeSeconds();
        this.completionXpAwarded = true;
    }

    void markAbandoned(Instant now) {
        this.status = SessionStatus.ABANDONED;
        this.abandonedAt = now;
        this.activeSegmentStartedAt = null;
    }

    void markInterrupted() {
        this.status = SessionStatus.INTERRUPTED;
        this.activeSegmentStartedAt = null;
    }

    /**
     * Picks an interrupted session back up. Blocking is enforced again, and the stale heartbeat is
     * cleared so it cannot interrupt the session straight away; SessionService re-arms detection
     * if the extension is alive.
     */
    void resumeFromInterruption(Instant now) {
        this.status = SessionStatus.ACTIVE;
        this.activeSegmentStartedAt = now;
        this.blockingState = BlockingState.ACTIVE;
        this.lastHeartbeatAt = null;
    }

    void recordHeartbeat(Instant now) {
        this.lastHeartbeatAt = now;
    }

    Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    /**
     * Records that the user manually released website blocking after abandoning this session.
     * Blocking is released for good, so the state is OVERRIDE_USED rather than something the daily
     * streak target could later change. The session itself was already ended by the abandonment.
     */
    void markOverridden() {
        this.overrideUsed = true;
        this.blockingState = BlockingState.OVERRIDE_USED;
    }

    long uncreditedActiveSeconds() {
        return activeFocusSeconds - streakCreditedActiveSeconds;
    }

    long uncreditedPausedSeconds() {
        return finalizedPausedSeconds - streakCreditedPausedSeconds;
    }

    /** Records that everything accumulated so far has been credited to the streak. */
    void markStreakCredited() {
        this.streakCreditedActiveSeconds = activeFocusSeconds;
        this.streakCreditedPausedSeconds = finalizedPausedSeconds;
    }

    void updateBlockingState(BlockingState blockingState) {
        this.blockingState = blockingState;
    }

    Instant getActiveSegmentStartedAt() {
        return activeSegmentStartedAt;
    }

    /**
     * Active focus seconds as of {@code now}, including the still-running ACTIVE segment. The
     * stored {@code activeFocusSeconds} only advances on pause/complete/abandon, so callers that
     * display live progress need this instead.
     */
    public long activeSecondsAt(Instant now) {
        if (status != SessionStatus.ACTIVE || activeSegmentStartedAt == null) {
            return activeFocusSeconds;
        }
        return activeFocusSeconds + Math.max(0, Duration.between(activeSegmentStartedAt, now).getSeconds());
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public TaskMode getTaskMode() {
        return taskMode;
    }

    public TaskCategory getTaskCategory() {
        return taskCategory;
    }

    public int getPlannedFocusMinutes() {
        return plannedFocusMinutes;
    }

    public long getActiveFocusSeconds() {
        return activeFocusSeconds;
    }

    public long getFinalizedPausedSeconds() {
        return finalizedPausedSeconds;
    }

    public long getQualifyingSeconds() {
        return qualifyingSeconds;
    }

    public long getOvertimeSeconds() {
        return overtimeSeconds;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public BlockingState getBlockingState() {
        return blockingState;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getAbandonedAt() {
        return abandonedAt;
    }

    public boolean isOverrideUsed() {
        return overrideUsed;
    }

    public boolean isCompletionXpAwarded() {
        return completionXpAwarded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCameraVerification() {
        return cameraVerification;
    }

    /** Off-task time settled so far. Time still provisional in the running stretch is not included. */
    public long getOffTaskSeconds() {
        return offTaskSeconds;
    }

    /** Part of {@code activeFocusSeconds} already credited to the streak. Exported for backups. */
    public long getStreakCreditedActiveSeconds() {
        return streakCreditedActiveSeconds;
    }

    /** Part of {@code finalizedPausedSeconds} already credited to the streak. Exported for backups. */
    public long getStreakCreditedPausedSeconds() {
        return streakCreditedPausedSeconds;
    }
}
