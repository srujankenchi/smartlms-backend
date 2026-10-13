package com.smartlms.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.smartlms.exception.BadRequestException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Robust Jackson deserializer for LocalDateTime that flexibly handles:
 * - ISO-8601 UTC / offset strings (e.g. "2026-10-25T00:00:00.000Z")
 * - ISO local date-time strings (e.g. "2026-10-25T23:59:59")
 * - Simple date strings (e.g. "2026-10-25") defaulted to 23:59:59
 * - Rejects malformed strings with a clear HTTP 400 BadRequestException.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();

        // 1. Try ISO-8601 with timezone/UTC offset (e.g. 2026-10-25T00:00:00.000Z or +05:30)
        if (text.endsWith("Z") || text.contains("+")) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
            } catch (DateTimeParseException ignored) {}
        }

        // 2. Try standard ISO LocalDateTime (e.g. 2026-10-25T14:30:00)
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {}

        // 3. Try plain Date (e.g. 2026-10-25) -> set to end of due date (23:59:59)
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atTime(23, 59, 59);
        } catch (DateTimeParseException ignored) {}

        // 4. Invalid date format
        throw new BadRequestException("Invalid date format: '" + text + "'. Expected YYYY-MM-DD or ISO-8601 format.");
    }
}
