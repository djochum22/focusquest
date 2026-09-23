package com.example.focusquest.session;

import com.example.focusquest.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Optional<FocusSession> findFirstByUserAndStatusIn(User user, Collection<SessionStatus> statuses);

    Optional<FocusSession> findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(User user);

    List<FocusSession> findByUserAndStatusInOrderByStartedAtDescIdDesc(User user, Collection<SessionStatus> statuses,
                                                                        Pageable pageable);
}
