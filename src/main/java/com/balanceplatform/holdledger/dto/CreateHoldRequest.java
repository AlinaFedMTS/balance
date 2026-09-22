package com.balanceplatform.holdledger.dto;

import com.balanceplatform.holdledger.domain.HoldType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

/**
 * Request body for {@code POST /internal/v1/holds} (FR-001).
 *
 * <p>Mirrors the fields the Temporal worker derives from the processing center
 * {@code CardAuthorizedEvent} (ФТ-BALANCE-001 §6). Creation is idempotent by
 * {@link #operationId} (FR-010): a repeat with identical {@link #accountId}, {@link #amount}
 * and {@link #currency} returns the existing hold; a repeat with differing values is rejected.
 *
 * @param operationId  business key of the hold, identical to the AUTH event's {@code operationId}
 * @param accountId    account the hold is placed against
 * @param amount       held amount, must be strictly positive
 * @param currency     ISO-4217 currency code
 * @param cardId       tokenized card identifier (never a PAN, NFR-006)
 * @param rrn          retrieval reference number, used for matching when no {@code relatedOperationId} is sent (FR-007)
 * @param holdType     direction of the underlying authorization
 * @param authorizedAt timestamp of the original authorization
 */
@Builder
@Schema(description = "Command to create a new card hold, idempotent by operationId")
public record CreateHoldRequest(

        @Schema(description = "Business key of the hold (processing center operationId)", example = "op-7f3e1c2a")
        @NotBlank
        String operationId,

        @Schema(description = "Account identifier the hold is placed against", example = "40817810000000000001")
        @NotBlank
        String accountId,

        @Schema(description = "Held amount, strictly positive", example = "1500.00")
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code", example = "RUB")
        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @Schema(description = "Tokenized card identifier; never a PAN", example = "tok_9f8e7d6c")
        @NotBlank
        String cardId,

        @Schema(description = "Retrieval reference number used for matching", example = "123456789012")
        @NotBlank
        String rrn,

        @Schema(description = "Direction of the underlying card authorization")
        @NotNull
        HoldType holdType,

        @Schema(description = "Timestamp of the original authorization", example = "2026-09-19T10:15:30Z")
        @NotNull
        @PastOrPresent
        Instant authorizedAt) {
}
