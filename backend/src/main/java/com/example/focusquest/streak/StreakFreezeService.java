package com.example.focusquest.streak;

import com.example.focusquest.progression.GemService;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Buying streak freezes and reporting the user's inventory. Spending them is StreakService's job,
 * when a daily target is reached after missed days (see {@link StreakService}).
 */
@Service
public class StreakFreezeService {

    private static final int RECENTLY_USED_LIMIT = 5;

    private final StreakFreezeRepository streakFreezeRepository;
    private final GemService gemService;
    private final ClockProvider clockProvider;
    private final int price;
    private final int maxOwned;

    public StreakFreezeService(StreakFreezeRepository streakFreezeRepository, GemService gemService,
                               ClockProvider clockProvider,
                               @Value("${focusquest.streak-freeze.price}") int price,
                               @Value("${focusquest.streak-freeze.max-owned}") int maxOwned) {
        if (price <= 0 || maxOwned <= 0) {
            throw new IllegalArgumentException("focusquest.streak-freeze.price and max-owned must be positive");
        }
        this.streakFreezeRepository = streakFreezeRepository;
        this.gemService = gemService;
        this.clockProvider = clockProvider;
        this.price = price;
        this.maxOwned = maxOwned;
    }

    /** Buys one freeze. Refused (409) if the user already holds the most allowed or cannot afford it. */
    @Transactional
    public FreezeInventoryResponse purchase(User user) {
        if (streakFreezeRepository.countByUserAndUsedPeriodIsNull(user) >= maxOwned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already hold " + maxOwned + " streak freezes, the most allowed");
        }
        long balance = gemService.getBalance(user);
        if (balance < price) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A streak freeze costs " + price + " gems and you have " + balance);
        }
        StreakFreeze freeze = streakFreezeRepository.save(new StreakFreeze(user, clockProvider.now()));
        gemService.chargeFreezePurchase(user, freeze.getId(), price);
        return inventory(user);
    }

    @Transactional(readOnly = true)
    public FreezeInventoryResponse inventory(User user) {
        List<StreakFreeze> freezes = streakFreezeRepository.findByUserOrderByIdAsc(user);
        List<FreezeInventoryResponse.UsedFreeze> recentlyUsed = freezes.stream()
                .filter(StreakFreeze::isUsed)
                .sorted(Comparator.comparing(StreakFreeze::getUsedAt)
                        .thenComparing(freeze -> freeze.getUsedPeriod().getStartTime()).reversed())
                .limit(RECENTLY_USED_LIMIT)
                .map(freeze -> new FreezeInventoryResponse.UsedFreeze(freeze.getUsedPeriod().getStartTime(),
                        freeze.getUsedPeriod().getEndTime(), freeze.getUsedAt()))
                .toList();
        int owned = (int) freezes.stream().filter(freeze -> !freeze.isUsed()).count();
        return new FreezeInventoryResponse(owned, maxOwned, price, gemService.getBalance(user), recentlyUsed);
    }
}
