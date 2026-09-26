package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OffTaskDisputeRepository extends JpaRepository<OffTaskDispute, Long> {

    List<OffTaskDispute> findBySession(FocusSession session);

    boolean existsBySessionAndEpisodeStartedAt(FocusSession session, Instant episodeStartedAt);

    List<OffTaskDispute> findBySessionUserOrderByIdAsc(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OffTaskDispute d where d.session in (select s from FocusSession s where s.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
