package com.example.focusquest.session;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionPauseRepository extends JpaRepository<SessionPause, Long> {

    Optional<SessionPause> findFirstBySessionAndFinalizedFalse(FocusSession session);

    List<SessionPause> findBySessionUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SessionPause p where p.session in (select s from FocusSession s where s.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
