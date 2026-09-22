package com.balanceplatform.holdledger.mapper;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.dto.CreateHoldRequest;
import com.balanceplatform.holdledger.dto.HoldResponse;

/**
 * Maps between the {@link Hold} domain aggregate and its request/response DTOs.
 */
public final class HoldMapper {

    private HoldMapper() {
    }

    /**
     * Builds a new {@link HoldStatus#PENDING} {@link Hold} from a creation request.
     *
     * @param request validated creation request
     * @return a new, unpersisted {@link Hold} in {@code PENDING} status
     */
    public static Hold toNewHold(CreateHoldRequest request) {
        return Hold.builder()
                .operationId(request.operationId())
                .accountId(request.accountId())
                .amount(request.amount())
                .currency(request.currency())
                .cardId(request.cardId())
                .rrn(request.rrn())
                .holdType(request.holdType())
                .status(HoldStatus.PENDING)
                .authorizedAt(request.authorizedAt())
                .build();
    }

    /**
     * Projects a {@link Hold} into its API response representation (FR-011).
     *
     * @param hold hold to project
     * @return the corresponding {@link HoldResponse}
     */
    public static HoldResponse toResponse(Hold hold) {
        return HoldResponse.builder()
                .operationId(hold.getOperationId())
                .accountId(hold.getAccountId())
                .amount(hold.getAmount())
                .currency(hold.getCurrency())
                .cardId(hold.getCardId())
                .rrn(hold.getRrn())
                .holdType(hold.getHoldType())
                .status(hold.getStatus())
                .reason(hold.getReason())
                .documentId(hold.getDocumentId())
                .authorizedAt(hold.getAuthorizedAt())
                .resolvedAt(hold.getResolvedAt())
                .expiresAt(hold.expiresAt())
                .build();
    }
}
