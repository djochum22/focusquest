package com.example.focusquest.streak;

import com.example.focusquest.user.User;
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
 * A streak freeze bought with gems. It is unused until StreakService spends it on a missed daily
 * period, and it can only cover a period that ended after it was bought.
 */
@Entity
@Table(name = "streak_freezes")
public class StreakFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    private Instant purchasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_period_id")
    private StreakPeriod usedPeriod;

    @Column(name = "used_at")
    private Instant usedAt;

    protected StreakFreeze() {
    }

    public StreakFreeze(User user, Instant purchasedAt) {
        this.user = user;
        this.purchasedAt = purchasedAt;
    }

    /** Rebuilds a freeze from a backup, exactly as it was recorded. Only restoring a data export uses it. */
    public static StreakFreeze restore(User user, Instant purchasedAt, StreakPeriod usedPeriod, Instant usedAt) {
        StreakFreeze freeze = new StreakFreeze(user, purchasedAt);
        freeze.usedPeriod = usedPeriod;
        freeze.usedAt = usedAt;
        return freeze;
    }

    /** Whether this freeze was already owned when a period ending at {@code periodEnd} ended, so it may cover it. */
    boolean canCover(Instant periodEnd) {
        return purchasedAt.isBefore(periodEnd);
    }

    void spendOn(StreakPeriod period, Instant now) {
        this.usedPeriod = period;
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public StreakPeriod getUsedPeriod() {
        return usedPeriod;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedPeriod != null;
    }
}
