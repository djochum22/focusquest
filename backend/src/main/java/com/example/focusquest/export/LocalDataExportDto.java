package com.example.focusquest.export;

import java.time.Instant;

/**
 * The full local data export. Intentionally minimal for now: none of the domain
 * modules (user, session, streak, blocking, progression) are implemented yet, so
 * there is nothing to export beyond the export metadata itself. As each module is
 * built, add its data here as an additional field (for example {@code UserDto user},
 * {@code List<FocusSessionDto> focusSessions}) and populate it in {@link ExportService}.
 */
public record LocalDataExportDto(
        Instant exportedAt,
        String schemaVersion
) {
}
