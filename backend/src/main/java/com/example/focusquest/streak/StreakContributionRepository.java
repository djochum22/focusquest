package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StreakContributionRepository extends JpaRepository<StreakContribution, Long> {

    List<StreakContribution> findByStreakPeriod(StreakPeriod streakPeriod);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StreakContribution c where c.streakPeriod in (select p from StreakPeriod p where p.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
