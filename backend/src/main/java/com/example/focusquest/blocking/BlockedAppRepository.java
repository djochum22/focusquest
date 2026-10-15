package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every lookup is scoped to the owning user. */
public interface BlockedAppRepository extends JpaRepository<BlockedApp, Long> {

    List<BlockedApp> findByUserOrderByIdAsc(User user);

    List<BlockedApp> findByUserAndActiveTrueOrderByIdAsc(User user);

    Optional<BlockedApp> findByIdAndUser(Long id, User user);

    boolean existsByUserAndNameIgnoreCase(User user, String name);

    boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlockedApp a where a.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
