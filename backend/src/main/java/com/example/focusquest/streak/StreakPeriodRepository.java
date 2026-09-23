package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface StreakPeriodRepository extends JpaRepository<StreakPeriod, Long> {

    Optional<StreakPeriod> findByUserAndPeriodTypeAndStartTime(User user, StreakPeriodType periodType, Instant startTime);
}
