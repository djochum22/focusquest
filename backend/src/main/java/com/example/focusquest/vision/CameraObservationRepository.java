package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CameraObservationRepository extends JpaRepository<CameraObservation, Long> {

    List<CameraObservation> findBySessionOrderByStartedAtAscIdAsc(FocusSession session);

    Optional<CameraObservation> findBySessionAndClientEventId(FocusSession session, String clientEventId);

    List<CameraObservation> findBySessionUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CameraObservation o where o.session in (select s from FocusSession s where s.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
