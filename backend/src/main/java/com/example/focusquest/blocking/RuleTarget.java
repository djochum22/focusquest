package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * State shared by {@link BlockedTarget} and {@link AllowlistTarget}: both are a user-owned,
 * normalized {@link UrlRule} that can be switched on and off. The two are separate tables and
 * entities because they play opposite roles in {@link RulePrecedence}.
 */
@MappedSuperclass
public abstract class RuleTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    // Always the canonical UrlRule.value() form, so uniqueness per user is a plain string compare.
    @Column(name = "target_value", nullable = false, length = 253)
    private String targetValue;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected RuleTarget() {
    }

    protected RuleTarget(User user, UrlRule rule, String displayName, boolean active) {
        this.user = user;
        this.targetType = rule.type();
        this.targetValue = rule.value();
        this.displayName = displayName;
        this.active = active;
    }

    // Package-private: only BlockingService may change a rule, so validation and the
    // enforcement lock stay centralized in the service layer.
    void update(UrlRule rule, String displayName, boolean active) {
        this.targetType = rule.type();
        this.targetValue = rule.value();
        this.displayName = displayName;
        this.active = active;
    }

    public UrlRule rule() {
        return UrlRule.parse(targetValue);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }
}
