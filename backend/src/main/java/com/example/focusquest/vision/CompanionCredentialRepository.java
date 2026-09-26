package com.example.focusquest.vision;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanionCredentialRepository extends JpaRepository<CompanionCredential, Long> {

    Optional<CompanionCredential> findByTokenHash(String tokenHash);

    Optional<CompanionCredential> findByUser(User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanionCredential c where c.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
