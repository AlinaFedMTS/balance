package com.balanceplatform.holdledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

/**
 * Response body for {@code POST /internal/v1/holds/expire-stale} (FR-005).
 *
 * <p>[ASSUMPTION] The exact response shape is not specified by CONTRACT-hold-ledger-v1; this
 * minimal projection reports how many holds were expired and which ones, for observability
 * (the {@code reconciliation_expired_total} metric described in FR-005/FR-012 is derived from it).
 *
 * @param expiredCount     number of holds expired by this invocation
 * @param expiredOperationIds business keys of the expired holds
 */
@Builder
@Schema(description = "Result of a stale-hold reconciliation run")
public record ExpireStaleResponse(

        @Schema(description = "Number of holds expired in this batch", example = "3")
        int expiredCount,

        @Schema(description = "Business keys of the expired holds")
        List<String> expiredOperationIds) {
}
