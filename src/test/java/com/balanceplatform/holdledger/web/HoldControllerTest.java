package com.balanceplatform.holdledger.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.domain.HoldType;
import com.balanceplatform.holdledger.exception.ConflictTerminalException;
import com.balanceplatform.holdledger.exception.GlobalExceptionHandler;
import com.balanceplatform.holdledger.exception.HoldNotFoundException;
import com.balanceplatform.holdledger.exception.IdempotencyConflictException;
import com.balanceplatform.holdledger.exception.ThresholdTooLowException;
import com.balanceplatform.holdledger.service.ExpireStaleResult;
import com.balanceplatform.holdledger.service.HoldCreationResult;
import com.balanceplatform.holdledger.service.HoldLedgerService;
import com.balanceplatform.holdledger.service.HoldReleaseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link HoldController}, exercising the internal API's HTTP contract
 * (status codes, validation, error mapping) against a mocked {@link HoldLedgerService}
 * (ФТ-BALANCE-001 FR-001, FR-002, FR-003, FR-005, FR-010, FR-011).
 */
@ExtendWith(MockitoExtension.class)
class HoldControllerTest {

    private static final String OPERATION_ID = "op-7f3e1c2a";
    private static final String ACCOUNT_ID = "40817810000000000001";
    private static final Instant FIXED_NOW = Instant.parse("2026-09-21T16:00:00Z");

    @Mock
    private HoldLedgerService holdLedgerService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new HoldController(holdLedgerService))
                .setControllerAdvice(new GlobalExceptionHandler(fixedClock))
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private Hold pendingHold() {
        return Hold.builder()
                .operationId(OPERATION_ID)
                .accountId(ACCOUNT_ID)
                .amount(new BigDecimal("1500.00"))
                .currency("RUB")
                .cardId("tok_9f8e7d6c")
                .rrn("123456789012")
                .holdType(HoldType.DEBIT)
                .status(HoldStatus.PENDING)
                .authorizedAt(FIXED_NOW.minusSeconds(60))
                .build();
    }

    private String createHoldRequestJson() throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "operationId", OPERATION_ID,
                "accountId", ACCOUNT_ID,
                "amount", "1500.00",
                "currency", "RUB",
                "cardId", "tok_9f8e7d6c",
                "rrn", "123456789012",
                "holdType", "DEBIT",
                "authorizedAt", FIXED_NOW.minusSeconds(60).toString()));
    }

    @Test
    void createHold_whenNewHold_thenReturns201WithBody() throws Exception {
        when(holdLedgerService.createHold(any())).thenReturn(new HoldCreationResult(pendingHold(), true));

        mockMvc.perform(post("/internal/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHoldRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value(OPERATION_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createHold_whenIdempotentReplay_thenReturns200() throws Exception {
        when(holdLedgerService.createHold(any())).thenReturn(new HoldCreationResult(pendingHold(), false));

        mockMvc.perform(post("/internal/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHoldRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(OPERATION_ID));
    }

    @Test
    void createHold_whenInvalidPayload_thenReturns400() throws Exception {
        String invalidJson = objectMapper.writeValueAsString(java.util.Map.of(
                "operationId", "",
                "accountId", ACCOUNT_ID,
                "amount", "-1",
                "currency", "R",
                "cardId", "tok",
                "rrn", "rrn",
                "holdType", "DEBIT",
                "authorizedAt", FIXED_NOW.toString()));

        mockMvc.perform(post("/internal/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createHold_whenIdempotencyConflict_thenReturns409() throws Exception {
        when(holdLedgerService.createHold(any())).thenThrow(new IdempotencyConflictException(OPERATION_ID));

        mockMvc.perform(post("/internal/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHoldRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void getHold_whenExists_thenReturns200() throws Exception {
        when(holdLedgerService.getHold(OPERATION_ID)).thenReturn(pendingHold());

        mockMvc.perform(get("/internal/v1/holds/{operationId}", OPERATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(OPERATION_ID));
    }

    @Test
    void getHold_whenNotFound_thenReturns404() throws Exception {
        when(holdLedgerService.getHold(OPERATION_ID)).thenThrow(new HoldNotFoundException(OPERATION_ID));

        mockMvc.perform(get("/internal/v1/holds/{operationId}", OPERATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
    }

    @Test
    void searchHolds_whenQueried_thenReturns200WithList() throws Exception {
        when(holdLedgerService.searchHolds(eq("123456789012"), eq(ACCOUNT_ID), isNull()))
                .thenReturn(List.of(pendingHold()));

        mockMvc.perform(get("/internal/v1/holds")
                        .param("rrn", "123456789012")
                        .param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].operationId").value(OPERATION_ID));
    }

    @Test
    void releaseHold_whenStateChanged_thenReturns200() throws Exception {
        Hold matched = pendingHold().release(HoldStatus.MATCHED_IN_ABS, "ABS_POSTED", "doc-1", FIXED_NOW);
        when(holdLedgerService.release(eq(OPERATION_ID), any())).thenReturn(new HoldReleaseResult(matched, true));

        String requestJson = objectMapper.writeValueAsString(java.util.Map.of(
                "status", "MATCHED_IN_ABS",
                "reason", "ABS_POSTED",
                "documentId", "doc-1"));

        mockMvc.perform(post("/internal/v1/holds/{operationId}/release", OPERATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED_IN_ABS"));
    }

    @Test
    void releaseHold_whenConflictTerminal_thenReturns409() throws Exception {
        when(holdLedgerService.release(eq(OPERATION_ID), any()))
                .thenThrow(new ConflictTerminalException(OPERATION_ID, HoldStatus.REVERSED, HoldStatus.MATCHED_IN_ABS));

        String requestJson = objectMapper.writeValueAsString(java.util.Map.of(
                "status", "MATCHED_IN_ABS",
                "reason", "ABS_POSTED",
                "documentId", "doc-1"));

        mockMvc.perform(post("/internal/v1/holds/{operationId}/release", OPERATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT_TERMINAL"));
    }

    @Test
    void expireStale_whenThresholdTooLow_thenReturns400() throws Exception {
        when(holdLedgerService.expireStalePendingHolds(100)).thenThrow(new ThresholdTooLowException(100, 168));

        String requestJson = objectMapper.writeValueAsString(java.util.Map.of("olderThanHours", 100));

        mockMvc.perform(post("/internal/v1/holds/expire-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THRESHOLD_TOO_LOW"));
    }

    @Test
    void expireStale_whenValidThreshold_thenReturns200WithExpiredCount() throws Exception {
        Hold expired = pendingHold().release(HoldStatus.EXPIRED, "STALE_RECONCILIATION", null, FIXED_NOW);
        when(holdLedgerService.expireStalePendingHolds(216)).thenReturn(new ExpireStaleResult(List.of(expired)));

        String requestJson = objectMapper.writeValueAsString(java.util.Map.of("olderThanHours", 216));

        mockMvc.perform(post("/internal/v1/holds/expire-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredCount").value(1))
                .andExpect(jsonPath("$.expiredOperationIds[0]").value(OPERATION_ID));
    }
}
