package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A domain or URL path that stays reachable even when a broader block rule matches it. */
@Entity
@Table(name = "allowlist_targets")
public class AllowlistTarget extends RuleTarget {

    protected AllowlistTarget() {
    }

    public AllowlistTarget(User user, UrlRule rule, String displayName, boolean active) {
        super(user, rule, displayName, active);
    }
}
