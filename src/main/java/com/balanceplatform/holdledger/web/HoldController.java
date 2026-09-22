package com.balanceplatform.holdledger.web;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.dto.CreateHoldRequest;
import com.balanceplatform.holdledger.dto.ErrorResponse;
import com.balanceplatform.holdledger.dto.ExpireStaleRequest;
import com.balanceplatform.holdledger.dto.ExpireStaleResponse;
import com.balanceplatform.holdledger.dto.HoldResponse;
import com.balanceplatform.holdledger.dto.ReleaseHoldRequest;
import com.balanceplatform.holdledger.mapper.HoldMapper;
import com.balanceplatform.holdledger.service.ExpireStaleResult;
import com.balanceplatform.holdledger.service.HoldCreationResult;
import com.balanceplatform.holdledger.service.HoldLedgerService;
import com.balanceplatform.holdledger.service.HoldReleaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST API of the Hold Ledger service (ФТ-BALANCE-001, CONTRACT-hold-ledger-v1).
 *
 * <p>Consumed exclusively by the Temporal worker and the reconciliation job (NFR-006); there is
 * no synchronous, client-facing traffic on this API. Exposes:
 * <ul>
 *   <li>{@code POST /internal/v1/holds} &mdash; create a hold on AUTH (FR-001)</li>
 *   <li>{@code GET /internal/v1/holds/{operationId}} &mdash; read a hold by business key (FR-011)</li>
 *   <li>{@code GET /internal/v1/holds} &mdash; search holds by rrn/accountId/status (FR-007, FR-011)</li>
 *   <li>{@code POST /internal/v1/holds/{operationId}/release} &mdash; release a hold on ABS match
 *       or PC reverse (FR-002, FR-003, FR-004)</li>
 *   <li>{@code POST /internal/v1/holds/expire-stale} &mdash; reconciliation safety net (FR-005)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/holds")
@Validated
@Tag(name = "Hold Ledger", description = "Internal API for card hold lifecycle management")
public class HoldController {

    private final HoldLedgerService holdLedgerService;

    /**
     * Creates the controller.
     *
     * @param holdLedgerService business logic service backing this API
     */
    public HoldController(HoldLedgerService holdLedgerService) {
        this.holdLedgerService = holdLedgerService;
    }

    /**
     * Creates a hold for a card authorization, or returns the existing one on an idempotent
     * replay (FR-001, FR-010).
     *
     * @param request validated creation request
     * @return {@code 201} with the new hold, or {@code 200} with the existing one on replay
     */
    @PostMapping
    @Operation(summary = "Create a card hold", description = "Idempotent by operationId (FR-001, FR-010)")
    @ApiResponse(responseCode = "201", description = "Hold created",
            content = @Content(schema = @Schema(implementation = HoldResponse.class)))
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an existing hold",
            content = @Content(schema = @Schema(implementation = HoldResponse.class)))
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_CONFLICT: same operationId with different attributes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<HoldResponse> createHold(@Valid @RequestBody CreateHoldRequest request) {
        HoldCreationResult result = holdLedgerService.createHold(request);
        HoldResponse body = HoldMapper.toResponse(result.hold());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Reads a single hold by its business key (FR-011).
     *
     * @param operationId business key of the hold
     * @return {@code 200} with the hold
     */
    @GetMapping("/{operationId}")
    @Operation(summary = "Get a hold by operationId")
    @ApiResponse(responseCode = "200", description = "Hold found",
            content = @Content(schema = @Schema(implementation = HoldResponse.class)))
    @ApiResponse(responseCode = "404", description = "HOLD_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<HoldResponse> getHold(
            @Parameter(description = "Business key of the hold", example = "op-7f3e1c2a")
            @PathVariable String operationId) {
        Hold hold = holdLedgerService.getHold(operationId);
        return ResponseEntity.ok(HoldMapper.toResponse(hold));
    }

    /**
     * Searches holds by RRN, account and/or status (FR-007, FR-011). Used by the Temporal
     * worker to identify a hold when {@code relatedOperationId} is not present on the ABS
     * posting notification.
     *
     * @param rrn       optional RRN filter
     * @param accountId optional account filter
     * @param status    optional status filter
     * @return {@code 200} with the (possibly empty) list of matching holds
     */
    @GetMapping
    @Operation(summary = "Search holds", description = "Used for RRN-based identification (FR-007) and general read access (FR-011)")
    @ApiResponse(responseCode = "200", description = "Matching holds",
            content = @Content(schema = @Schema(implementation = HoldResponse.class)))
    public ResponseEntity<List<HoldResponse>> searchHolds(
            @Parameter(description = "Retrieval reference number filter", example = "123456789012")
            @RequestParam(required = false) String rrn,
            @Parameter(description = "Account identifier filter", example = "40817810000000000001")
            @RequestParam(required = false) String accountId,
            @Parameter(description = "Status filter")
            @RequestParam(required = false) HoldStatus status) {
        List<HoldResponse> holds = holdLedgerService.searchHolds(rrn, accountId, status).stream()
                .map(HoldMapper::toResponse)
                .toList();
        return ResponseEntity.ok(holds);
    }

    /**
     * Releases a hold into a terminal status following an ABS posting match, a processing
     * center reverse, or a TTL expiration decided upstream (FR-002, FR-003, FR-004).
     *
     * <p>Idempotent per FR-010: repeating the same target status returns {@code 200} without a
     * new lifecycle event; requesting a different terminal status than the one already
     * persisted is rejected with {@code 409}.
     *
     * @param operationId business key of the hold to release
     * @param request     validated release request
     * @return {@code 200} with the resulting hold state
     */
    @PostMapping("/{operationId}/release")
    @Operation(summary = "Release a hold to a terminal status", description = "Idempotent, claim-before-effect (FR-010)")
    @ApiResponse(responseCode = "200", description = "Hold released or already in the requested terminal status",
            content = @Content(schema = @Schema(implementation = HoldResponse.class)))
    @ApiResponse(responseCode = "404", description = "HOLD_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONFLICT_TERMINAL: hold already terminal with a different status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<HoldResponse> releaseHold(
            @Parameter(description = "Business key of the hold", example = "op-7f3e1c2a")
            @PathVariable String operationId,
            @Valid @RequestBody ReleaseHoldRequest request) {
        HoldReleaseResult result = holdLedgerService.release(operationId, request);
        return ResponseEntity.ok(HoldMapper.toResponse(result.hold()));
    }

    /**
     * Expires {@code PENDING} holds older than a safety threshold (FR-005), invoked
     * periodically by the reconciliation scheduler.
     *
     * @param request validated reconciliation request
     * @return {@code 200} with the count and ids of expired holds
     */
    @PostMapping("/expire-stale")
    @Operation(summary = "Expire stale pending holds", description = "Reconciliation safety net (FR-005)")
    @ApiResponse(responseCode = "200", description = "Stale holds expired",
            content = @Content(schema = @Schema(implementation = ExpireStaleResponse.class)))
    @ApiResponse(responseCode = "400", description = "THRESHOLD_TOO_LOW: olderThanHours below HoldPolicy.HOLD_TTL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ExpireStaleResponse> expireStale(@Valid @RequestBody ExpireStaleRequest request) {
        ExpireStaleResult result = holdLedgerService.expireStalePendingHolds(request.olderThanHours());
        ExpireStaleResponse body = ExpireStaleResponse.builder()
                .expiredCount(result.expiredHolds().size())
                .expiredOperationIds(result.expiredHolds().stream().map(Hold::getOperationId).toList())
                .build();
        return ResponseEntity.ok(body);
    }
}
