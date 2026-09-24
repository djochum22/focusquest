package com.example.focusquest.progression;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GemTransactionRepository extends JpaRepository<GemTransaction, Long> {

    boolean existsByUserAndTypeAndReferenceTypeAndReferenceId(
            User user, GemTransactionType type, String referenceType, Long referenceId);

    List<GemTransaction> findByUserOrderByIdAsc(User user);

    @Query("select coalesce(sum(t.amount), 0) from GemTransaction t where t.user = :user")
    long sumAmountByUser(@Param("user") User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GemTransaction t where t.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
