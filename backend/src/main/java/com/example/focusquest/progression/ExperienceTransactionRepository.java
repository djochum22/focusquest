package com.example.focusquest.progression;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExperienceTransactionRepository extends JpaRepository<ExperienceTransaction, Long> {

    Optional<ExperienceTransaction> findByUserAndTypeAndReferenceTypeAndReferenceId(
            User user, ExperienceTransactionType type, String referenceType, Long referenceId);
}
