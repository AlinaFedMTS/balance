package com.balanceplatform.holdledger.dto;

import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.domain.HoldType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

/**
 * Projection of a {@code Hold} returned by the read endpoints (FR-011).
 *
 * @param operationId  business key of the hold
 * @param accountId    account the hold is placed against
 * @param amount       held amount
 * @param currency     ISO-4217 currency code
 * @param cardId       tokenized card identifier
 * @param rrn          retrieval reference number
 * @param holdType     direction of the underlying authorization
 * @param status       current lifecycle status
 * @param reason       reason for the terminal status, {@code null} while {@code PENDING}
 * @param documentId   ABS document id that matched this hold, if any
 * @param authorizedAt timestamp of the original authorization
 * @param resolvedAt   timestamp the hold reached its terminal status, {@code null} while {@code PENDING}
 * @param expiresAt    {@code authorizedAt + HoldPolicy.HOLD_TTL} (FR-011)
 */
@Builder
@Schema(description = "Projection of a card hold")
public record HoldResponse(

        @Schema(example = "op-7f3e1c2a")
        String operationId,

        @Schema(example = "40817810000000000001")
        String accountId,

        @Schema(example = "1500.00")
        BigDecimal amount,

        @Schema(example = "RUB")
        String currency,

        @Schema(example = "tok_9f8e7d6c")
        String cardId,

        @Schema(example = "123456789012")
        String rrn,

        HoldType holdType,

        HoldStatus status,

        @Schema(example = "ABS_POSTED", nullable = true)
        String reason,

        @Schema(example = "doc-556677", nullable = true)
        String documentId,

        @Schema(example = "2026-09-19T10:15:30Z")
        Instant authorizedAt,

        @Schema(example = "2026-09-19T10:20:00Z", nullable = true)
        Instant resolvedAt,

        @Schema(example = "2026-09-26T10:15:30Z")
        Instant expiresAt) {
}
