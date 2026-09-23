package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A domain or URL path the user wants blocked while enforcement is active. */
@Entity
@Table(name = "blocked_targets")
public class BlockedTarget extends RuleTarget {

    protected BlockedTarget() {
    }

    public BlockedTarget(User user, UrlRule rule, String displayName, boolean active) {
        super(user, rule, displayName, active);
    }
}
