package com.example.focusquest.streak;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StreakContributionRepository extends JpaRepository<StreakContribution, Long> {

    List<StreakContribution> findByStreakPeriod(StreakPeriod streakPeriod);
}
