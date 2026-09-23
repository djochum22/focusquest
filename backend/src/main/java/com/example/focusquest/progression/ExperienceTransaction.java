package com.example.focusquest.progression;

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

/**
 * One immutable entry in the user's XP ledger. A user's XP is the sum of their entries, so awards
 * and penalties are recorded rather than overwritten and can be audited later.
 */
@Entity
@Table(name = "experience_transactions")
public class ExperienceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Negative for penalties.
    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private ExperienceTransactionType type;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExperienceTransaction() {
    }

    public ExperienceTransaction(User user, int amount, ExperienceTransactionType type,
                                  String referenceType, Long referenceId, Instant createdAt) {
        this.user = user;
        this.amount = amount;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getAmount() {
        return amount;
    }

    public ExperienceTransactionType getType() {
        return type;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
