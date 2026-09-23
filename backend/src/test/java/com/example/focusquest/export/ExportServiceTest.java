package com.example.focusquest.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

    @Test
    void exportLocalDataUsesInjectedClockAndCurrentSchemaVersion() {
        Instant fixedInstant = Instant.parse("2026-01-15T10:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        ExportService exportService = new ExportService(fixedClock);

        LocalDataExportDto result = exportService.exportLocalData();

        assertThat(result.exportedAt()).isEqualTo(fixedInstant);
        assertThat(result.schemaVersion()).isEqualTo("1.0");
    }
}
