package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreakConfigurationRepository extends JpaRepository<StreakConfiguration, Long> {

    Optional<StreakConfiguration> findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            User user, StreakPeriodType periodType, Instant asOf);

    Optional<StreakConfiguration> findByIdAndUser(Long id, User user);

    List<StreakConfiguration> findByUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StreakConfiguration c where c.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
