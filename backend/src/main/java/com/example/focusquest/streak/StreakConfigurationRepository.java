package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface StreakConfigurationRepository extends JpaRepository<StreakConfiguration, Long> {

    Optional<StreakConfiguration> findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            User user, StreakPeriodType periodType, Instant asOf);
}
