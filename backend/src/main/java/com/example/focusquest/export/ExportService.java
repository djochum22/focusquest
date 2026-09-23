package com.example.focusquest.export;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Builds the full local data export. See {@link LocalDataExportDto} for why the
 * export currently carries only metadata.
 */
@Service
public class ExportService {

    private static final String SCHEMA_VERSION = "1.0";

    private final Clock clock;

    public ExportService(Clock clock) {
        this.clock = clock;
    }

    public LocalDataExportDto exportLocalData() {
        return new LocalDataExportDto(Instant.now(clock), SCHEMA_VERSION);
    }
}
