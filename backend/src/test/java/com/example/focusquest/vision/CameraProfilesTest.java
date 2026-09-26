package com.example.focusquest.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.focusquest.session.TaskCategory;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CameraProfilesTest {

    private static final Duration AWAY = Duration.ofMinutes(3);
    private static final Duration PHONE = Duration.ofSeconds(20);
    private static final Duration LOOKING_AWAY = Duration.ofSeconds(60);
    private static final Duration GRACE = Duration.ofSeconds(60);

    private final CameraProfiles profiles = new CameraProfiles(AWAY, PHONE, LOOKING_AWAY, GRACE, 0.7);

    @Test
    void everyCategoryHasAProfile() {
        assertThat(profiles.all()).extracting(CameraProfile::category).containsExactly(TaskCategory.values());
    }

    @Test
    void screenWorkChecksLookingAwayFromTheScreen() {
        for (TaskCategory category : new TaskCategory[] {
                TaskCategory.CODING, TaskCategory.WORK, TaskCategory.ADMINISTRATION}) {
            CameraProfile profile = profiles.forCategory(category);
            assertThat(profile.workArea()).isEqualTo(WorkArea.SCREEN);
            assertThat(profile.warningAfter()).containsEntry(OffTaskSignal.AWAY, AWAY)
                    .containsEntry(OffTaskSignal.PHONE, PHONE)
                    .containsEntry(OffTaskSignal.LOOKING_AWAY, LOOKING_AWAY);
        }
    }

    @Test
    void paperWorkAcceptsTheDeskAsWellAsTheScreen() {
        for (TaskCategory category : new TaskCategory[] {
                TaskCategory.STUDYING, TaskCategory.READING, TaskCategory.WRITING, TaskCategory.PLANNING}) {
            CameraProfile profile = profiles.forCategory(category);
            assertThat(profile.workArea()).isEqualTo(WorkArea.SCREEN_OR_DESK);
            assertThat(profile.warningAfter()).containsKey(OffTaskSignal.LOOKING_AWAY);
        }
    }

    @Test
    void anywhereChecksOnlyBeingAwayAndThePhone() {
        for (TaskCategory category : new TaskCategory[] {
                TaskCategory.CREATIVE_WORK, TaskCategory.OTHER, TaskCategory.TASK_FREE}) {
            CameraProfile profile = profiles.forCategory(category);
            assertThat(profile.workArea()).isEqualTo(WorkArea.ANYWHERE);
            assertThat(profile.warningAfter()).containsOnlyKeys(OffTaskSignal.AWAY, OffTaskSignal.PHONE);
        }
    }

    @Test
    void everyProfileSharesTheGraceAndMinimumConfidence() {
        assertThat(profiles.all()).allSatisfy(profile -> {
            assertThat(profile.grace()).isEqualTo(GRACE);
            assertThat(profile.minConfidence()).isEqualTo(0.7);
        });
    }

    @Test
    void refusesConfigurationThatCouldNeverWork() {
        assertThatThrownBy(() -> new CameraProfiles(Duration.ZERO, PHONE, LOOKING_AWAY, GRACE, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraProfiles(AWAY, PHONE, LOOKING_AWAY, Duration.ofSeconds(-1), 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraProfiles(AWAY, PHONE, LOOKING_AWAY, GRACE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraProfiles(AWAY, PHONE, LOOKING_AWAY, GRACE, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
