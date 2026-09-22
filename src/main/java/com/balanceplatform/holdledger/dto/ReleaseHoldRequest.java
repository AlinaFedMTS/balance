package com.balanceplatform.holdledger.dto;

import com.balanceplatform.holdledger.domain.HoldStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Request body for {@code POST /internal/v1/holds/{operationId}/release}, covering both the
 * ABS-posting match (FR-002, status {@code MATCHED_IN_ABS}) and the processing-center reverse
 * (FR-003, status {@code REVERSED}) use cases, as driven by the Temporal worker.
 *
 * <p>Repeating the same target status is treated as an idempotent no-op (FR-010); requesting a
 * different terminal status than the one already persisted is rejected with
 * {@code 409 CONFLICT_TERMINAL}.
 *
 * @param status     target terminal status; must not be {@link HoldStatus#PENDING}
 * @param reason     reason for the transition (e.g. reverse reason, {@code ABS_POSTED_AMOUNT_MISMATCH})
 * @param documentId ABS document id; expected when {@link #status} is {@link HoldStatus#MATCHED_IN_ABS}
 */
@Builder
@Schema(description = "Command to release a hold into a terminal status")
public record ReleaseHoldRequest(

        @Schema(description = "Target terminal status", example = "MATCHED_IN_ABS")
        @NotNull
        HoldStatus status,

        @Schema(description = "Reason for the transition", example = "ABS_POSTED")
        @NotBlank
        String reason,

        @Schema(description = "ABS document identifier, populated when status is MATCHED_IN_ABS", example = "doc-556677")
        String documentId) {
}
