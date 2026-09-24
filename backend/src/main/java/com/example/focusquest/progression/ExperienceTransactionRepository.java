package com.example.focusquest.progression;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExperienceTransactionRepository extends JpaRepository<ExperienceTransaction, Long> {

    Optional<ExperienceTransaction> findByUserAndTypeAndReferenceTypeAndReferenceId(
            User user, ExperienceTransactionType type, String referenceType, Long referenceId);

    List<ExperienceTransaction> findByUserOrderByIdAsc(User user);

    @Query("select coalesce(sum(t.amount), 0) from ExperienceTransaction t where t.user = :user")
    long sumAmountByUser(@Param("user") User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExperienceTransaction t where t.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
