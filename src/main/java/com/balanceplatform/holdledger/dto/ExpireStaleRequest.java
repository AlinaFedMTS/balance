package com.balanceplatform.holdledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * Request body for {@code POST /internal/v1/holds/expire-stale}, invoked periodically by the
 * reconciliation safety net (FR-005).
 *
 * <p>{@link #olderThanHours} must be at least {@code HoldPolicy.HOLD_TTL} (168 hours). This
 * lower bound is intentionally enforced in {@code HoldLedgerService}, not via Bean Validation
 * here, so that a value below it is rejected with the FT-mandated {@code 400 THRESHOLD_TOO_LOW}
 * business error code rather than a generic validation error. The operational default is
 * 216 hours (TTL + 48h safety margin).
 *
 * @param olderThanHours age threshold, in hours, measured from {@code authorizedAt}
 */
@Builder
@Schema(description = "Command to expire PENDING holds older than a safety threshold")
public record ExpireStaleRequest(

        @Schema(description = "Age threshold in hours, minimum 168 (7 days)", example = "216")
        @NotNull
        @Positive
        Integer olderThanHours) {
}
