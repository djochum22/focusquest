package com.example.focusquest.vision;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.focusquest.session.TaskCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The camera rules of section 21 with the default timings: phone 20 s, looking away 60 s, away 3 min, grace 60 s. */
class OffTaskCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-03-10T09:00:00Z");
    private static final Duration MERGE_GAP = Duration.ofSeconds(5);
    private static final Instant FAR = T0.plusSeconds(3600);

    private final CameraProfiles profiles = new CameraProfiles(Duration.ofMinutes(3), Duration.ofSeconds(20),
            Duration.ofSeconds(60), Duration.ofSeconds(60), 0.7);
    private final CameraProfile coding = profiles.forCategory(TaskCategory.CODING);

    private static OffTaskCalculator.Observed seen(OffTaskSignal signal, long from, long until) {
        return seen(signal, from, until, 0.9);
    }

    private static OffTaskCalculator.Observed seen(OffTaskSignal signal, long from, long until, double confidence) {
        return new OffTaskCalculator.Observed(signal, confidence, T0.plusSeconds(from), T0.plusSeconds(until));
    }

    private List<OffTaskCalculator.Episode> episodes(CameraProfile profile, OffTaskCalculator.Observed... observed) {
        return OffTaskCalculator.episodes(List.of(observed), profile, MERGE_GAP, FAR);
    }

    @Test
    void aPhoneHeldPastItsWarningTimeIsWarnedAndSubtractedAfterTheGrace() {
        List<OffTaskCalculator.Episode> episodes = episodes(coding, seen(OffTaskSignal.PHONE, 60, 240));

        assertThat(episodes).singleElement().satisfies(episode -> {
            assertThat(episode.startedAt()).isEqualTo(T0.plusSeconds(60));
            assertThat(episode.endedAt()).isEqualTo(T0.plusSeconds(240));
            assertThat(episode.warnedAt()).isEqualTo(T0.plusSeconds(80));
            assertThat(episode.deductionStartsAt()).isEqualTo(T0.plusSeconds(140));
        });
    }

    @Test
    void aGlanceIsNeverAWarning() {
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 15))).singleElement()
                .satisfies(episode -> assertThat(episode.warnedAt()).isNull());
    }

    @Test
    void comingBackWithinTheGraceSubtractsNothing() {
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 70))).singleElement().satisfies(episode -> {
            assertThat(episode.warnedAt()).isEqualTo(T0.plusSeconds(20));
            assertThat(episode.deducts()).isFalse();
        });
    }

    @Test
    void eachSignalHasItsOwnWarningTime() {
        assertThat(episodes(coding, seen(OffTaskSignal.LOOKING_AWAY, 0, 59)).getFirst().warnedAt()).isNull();
        assertThat(episodes(coding, seen(OffTaskSignal.LOOKING_AWAY, 0, 60)).getFirst().warnedAt())
                .isEqualTo(T0.plusSeconds(60));
        assertThat(episodes(coding, seen(OffTaskSignal.AWAY, 0, 179)).getFirst().warnedAt()).isNull();
        assertThat(episodes(coding, seen(OffTaskSignal.AWAY, 0, 180)).getFirst().warnedAt())
                .isEqualTo(T0.plusSeconds(180));
    }

    @Test
    void signalsDoNotAddUpTowardAWarning() {
        // 15 s on the phone, then 50 s looking away: one episode of 65 s, but no signal lasted long enough.
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 15), seen(OffTaskSignal.LOOKING_AWAY, 15, 65)))
                .singleElement().satisfies(episode -> {
                    assertThat(episode.endedAt()).isEqualTo(T0.plusSeconds(65));
                    assertThat(episode.warnedAt()).isNull();
                });
    }

    @Test
    void aWarningFromOneSignalCoversTheWholeEpisode() {
        // The phone warns at 20 s; looking away afterwards keeps the episode, and the subtraction, going.
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 30), seen(OffTaskSignal.LOOKING_AWAY, 30, 200)))
                .singleElement().satisfies(episode -> {
                    assertThat(episode.warnedAt()).isEqualTo(T0.plusSeconds(20));
                    assertThat(episode.deductionStartsAt()).isEqualTo(T0.plusSeconds(80));
                    assertThat(episode.endedAt()).isEqualTo(T0.plusSeconds(200));
                });
    }

    @Test
    void aShortGapIsFlickerButALongerOneIsAComeback() {
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 15), seen(OffTaskSignal.PHONE, 20, 40)))
                .singleElement().satisfies(episode -> assertThat(episode.warnedAt()).isEqualTo(T0.plusSeconds(20)));

        List<OffTaskCalculator.Episode> apart =
                episodes(coding, seen(OffTaskSignal.PHONE, 0, 15), seen(OffTaskSignal.PHONE, 21, 40));
        assertThat(apart).hasSize(2).allSatisfy(episode -> assertThat(episode.warnedAt()).isNull());
    }

    @Test
    void lowConfidenceObservationsAreIgnored() {
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 300, 0.69))).isEmpty();
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 300, 0.7))).hasSize(1);
    }

    @Test
    void lookingAwayIsNotCheckedWhereTheWorkAreaIsAnywhere() {
        CameraProfile creative = profiles.forCategory(TaskCategory.CREATIVE_WORK);

        assertThat(episodes(creative, seen(OffTaskSignal.LOOKING_AWAY, 0, 600))).isEmpty();
        assertThat(episodes(creative, seen(OffTaskSignal.PHONE, 0, 600))).hasSize(1);
    }

    @Test
    void nothingCountsPastTheHorizon() {
        List<OffTaskCalculator.Episode> episodes = OffTaskCalculator.episodes(
                List.of(seen(OffTaskSignal.PHONE, 0, 600)), coding, MERGE_GAP, T0.plusSeconds(50));

        assertThat(episodes).singleElement().satisfies(episode -> {
            assertThat(episode.endedAt()).isEqualTo(T0.plusSeconds(50));
            assertThat(episode.deducts()).isFalse();
        });
    }

    @Test
    void overlappingReportsOfTheSameSignalAreOneStretch() {
        assertThat(episodes(coding, seen(OffTaskSignal.PHONE, 0, 30), seen(OffTaskSignal.PHONE, 10, 25)))
                .singleElement().satisfies(episode -> assertThat(episode.endedAt()).isEqualTo(T0.plusSeconds(30)));
    }
}
