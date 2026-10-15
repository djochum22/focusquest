package com.example.focusquest.blocking;

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

/**
 * A Mac application the desktop app hides or quits while enforcement is active, on the same terms
 * as a {@link BlockedTarget}. The desktop app matches it by {@code bundleId} when there is one
 * ("com.tinyspeck.slackmacgap"), and otherwise by {@code name} against the app's own name, ignoring
 * case. There is no allowlist for apps: an app is either on this list or it is not.
 */
@Entity
@Table(name = "blocked_apps")
public class BlockedApp {

    /**
     * A bundle id as Apple allows it: letters, digits and hyphens in parts joined by dots, at least two
     * parts. Blank counts as none.
     */
    public static final String BUNDLE_ID_PATTERN = "^\\s*$|^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "bundle_id", length = 255)
    private String bundleId;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected BlockedApp() {
    }

    public BlockedApp(User user, String name, String bundleId, boolean active) {
        this.user = user;
        this.name = name;
        this.bundleId = bundleId;
        this.active = active;
    }

    // Package-private: only BlockingService may change an app, so the enforcement lock stays in one place.
    void update(String name, String bundleId, boolean active) {
        this.name = name;
        this.bundleId = bundleId;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getBundleId() {
        return bundleId;
    }

    public boolean isActive() {
        return active;
    }
}
