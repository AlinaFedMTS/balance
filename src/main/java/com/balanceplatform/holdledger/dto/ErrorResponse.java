package com.balanceplatform.holdledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;

/**
 * Standard error body returned by the Hold Ledger internal API.
 *
 * <p>[ASSUMPTION] CONTRACT-hold-ledger-v1 is not available in this task's context; the error
 * codes populated in {@link #code} ({@code IDEMPOTENCY_CONFLICT}, {@code CONFLICT_TERMINAL},
 * {@code THRESHOLD_TOO_LOW}, {@code HOLD_NOT_FOUND}, {@code VALIDATION_ERROR}) are taken
 * verbatim from ФТ-BALANCE-001 FR-005 and FR-010; this envelope shape carrying them is assumed.
 *
 * @param code      machine-readable error code
 * @param message   human-readable description
 * @param timestamp instant the error was produced
 */
@Builder
@Schema(description = "Standard error response")
public record ErrorResponse(

        @Schema(example = "CONFLICT_TERMINAL")
        String code,

        @Schema(example = "Hold op-7f3e1c2a is already REVERSED, cannot release as MATCHED_IN_ABS")
        String message,

        @Schema(example = "2026-09-21T16:00:00Z")
        Instant timestamp) {
}
