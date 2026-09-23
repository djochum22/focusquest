package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/** Queries shared by the block and allowlist repositories; every lookup is scoped to the owning user. */
@NoRepositoryBean
public interface RuleTargetRepository<T extends RuleTarget> extends JpaRepository<T, Long> {

    List<T> findByUserOrderByIdAsc(User user);

    List<T> findByUserAndActiveTrueOrderByIdAsc(User user);

    Optional<T> findByIdAndUser(Long id, User user);

    boolean existsByUserAndTargetValue(User user, String targetValue);

    boolean existsByUserAndTargetValueAndIdNot(User user, String targetValue, Long id);
}
