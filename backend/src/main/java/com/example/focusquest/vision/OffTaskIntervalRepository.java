package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OffTaskIntervalRepository extends JpaRepository<OffTaskInterval, Long> {

    List<OffTaskInterval> findBySessionOrderByDeductionStartedAtAsc(FocusSession session);

    List<OffTaskInterval> findBySessionUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OffTaskInterval i where i.session in (select s from FocusSession s where s.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
