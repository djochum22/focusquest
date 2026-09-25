package com.example.focusquest.auth;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExtensionCredentialRepository extends JpaRepository<ExtensionCredential, Long> {

    /** Fetches the user too: the authentication filter needs the username outside any transaction. */
    @Query("select c from ExtensionCredential c join fetch c.user where c.tokenHash = :tokenHash")
    Optional<ExtensionCredential> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExtensionCredential c where c.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
