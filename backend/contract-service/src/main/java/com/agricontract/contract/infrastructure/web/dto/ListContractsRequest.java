package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.common.PaginatedRequest;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ListContractsRequest extends PaginatedRequest {
    private String role;
    private ContractStatus status;
}
