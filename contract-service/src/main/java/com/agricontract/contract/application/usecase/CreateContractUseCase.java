package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateContractUseCase {

    private final ContractRepository contractRepository;
    private final ListingPort listingPort;
    private final UserPort userPort;

    public ContractResponse execute(CreateContractCommand command) {
        //feign
        ListingResponse listing = listingPort.getListing(command.listingId());

        if (!"ACTIVE".equals(listing.status())) {
            throw new IllegalArgumentException("Listing is not available: " + command.listingId());
        }

        UserInfo buyerInfo = userPort.getUser(command.buyerId());
        UserInfo sellerInfo = userPort.getUser(listing.sellerId());

        //idempotency
        ContractId contractId = (command.contractId() != null)
                ? new ContractId(command.contractId())
                : new ContractId(UUID.randomUUID().toString());

        Contract contract = Contract.offer(
                contractId,
                command.listingId(),
                command.buyerId(),
                listing.sellerId(),
                listing.productName(),
                buyerInfo.organizationName(),
                sellerInfo.organizationName(),
                buyerInfo.email(),
                sellerInfo.email(),
                command.terms()
        );

        contractRepository.save(contract);
        log.info("Contract {} offered: buyer={} seller={} listing={}", contractId.value(), command.buyerId(), listing.sellerId(), command.listingId());

        return ContractResponse.from(contract);
    }
}
