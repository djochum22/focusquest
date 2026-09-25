package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreakPeriodRepository extends JpaRepository<StreakPeriod, Long> {

    Optional<StreakPeriod> findByUserAndPeriodTypeAndStartTime(User user, StreakPeriodType periodType, Instant startTime);

    Optional<StreakPeriod> findFirstByUserAndPeriodTypeAndStartTimeLessThanEqualOrderByStartTimeDesc(
            User user, StreakPeriodType periodType, Instant instant);

    List<StreakPeriod> findByUserOrderByStartTimeAsc(User user);

    List<StreakPeriod> findByUserAndPeriodTypeAndStatusOrderByStartTimeDesc(
            User user, StreakPeriodType periodType, StreakPeriodStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StreakPeriod p where p.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
