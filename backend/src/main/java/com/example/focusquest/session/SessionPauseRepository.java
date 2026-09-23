package com.example.focusquest.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionPauseRepository extends JpaRepository<SessionPause, Long> {

    Optional<SessionPause> findFirstBySessionAndFinalizedFalse(FocusSession session);
}
