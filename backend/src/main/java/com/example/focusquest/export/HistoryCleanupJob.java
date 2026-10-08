package com.example.focusquest.export;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired history in the background, so it goes even while nobody opens the history page.
 * Reading the history also deletes expired sessions first, so the page is never behind.
 */
@Component
public class HistoryCleanupJob {

    private final HistoryService historyService;

    public HistoryCleanupJob(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Scheduled(initialDelayString = "${focusquest.history.cleanup-interval}",
               fixedDelayString = "${focusquest.history.cleanup-interval}")
    public void deleteExpiredHistory() {
        historyService.deleteExpiredForAllUsers();
    }
}
