package com.example.focusquest.vision;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CameraSettingsRepository extends JpaRepository<CameraSettings, Long> {

    Optional<CameraSettings> findByUser(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CameraSettings c where c.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
