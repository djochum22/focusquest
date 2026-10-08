package com.example.focusquest.session;

import com.example.focusquest.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Optional<FocusSession> findFirstByUserAndStatusIn(User user, Collection<SessionStatus> statuses);

    Optional<FocusSession> findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(User user);

    List<FocusSession> findByUserAndStatus(User user, SessionStatus status);

    List<FocusSession> findByUserAndStatusInOrderByStartedAtDescIdDesc(User user, Collection<SessionStatus> statuses,
                                                                        Pageable pageable);

    List<FocusSession> findByUserOrderByIdAsc(User user);

    List<FocusSession> findByUserAndStatusIn(User user, Collection<SessionStatus> statuses);

    List<FocusSession> findByUserAndStatusInAndStartedAtBefore(User user, Collection<SessionStatus> statuses,
                                                              Instant cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FocusSession s where s.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
