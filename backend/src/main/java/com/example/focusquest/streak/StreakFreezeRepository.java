package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StreakFreezeRepository extends JpaRepository<StreakFreeze, Long> {

    /** Unused freezes, oldest first: the order they are spent in. */
    List<StreakFreeze> findByUserAndUsedPeriodIsNullOrderByPurchasedAtAscIdAsc(User user);

    long countByUserAndUsedPeriodIsNull(User user);

    List<StreakFreeze> findByUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StreakFreeze f where f.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
