package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.UpdateContractTermsCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATCH /api/v1/contracts/{id}/terms (#93). The idempotency claim, the new terms and the contract row commit in
 * one transaction: if anything fails, the key is not consumed and the caller can retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateContractTermsUseCase {
    static final String OPERATION = "UPDATE_CONTRACT_TERMS";

    private final ContractRepository contractRepository;
    private final IdempotencyStore idempotencyStore;
    private final TermsCanonicalizer canonicalizer;
    private final ContractTermsRules rules;
    private final ContractTemplateProfile currentTemplate;

    @Transactional
    public CoffeeContractView execute(UpdateContractTermsCommand command) {
        String requestHash = CanonicalTerms.sha256(command.contractId() + "\n" + canonicalizer.canonicalInputJson(command.terms()));
        IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.actorId(), OPERATION,
                command.idempotencyKey(), requestHash);
        if (outcome == IdempotencyStore.Outcome.CONFLICT) {
            throw new IdempotencyKeyReusedException();
        }

        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        if (!command.actorId().equals(contract.getBuyerId()) && !command.actorId().equals(contract.getSellerId())) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }
        if (outcome == IdempotencyStore.Outcome.REPLAY) {
            log.info("Replayed terms update {} for contract {}", command.idempotencyKey(), command.contractId());
            return CoffeeContractView.from(contract);
        }
        if (contract.getCoffeeTerms() == null) {
            throw new ContractConflictException(ContractConflictException.LEGACY_TERMS,
                    "This contract has no coffee terms; create a new offer instead.");
        }

        // goodsTerms stay as frozen at offer time; the template is the current one (a revision always re-signs).
        ContractTermsSnapshot revised = ContractTermsSnapshot.assemble(command.terms(),
                contract.getCoffeeTerms().snapshot().goodsTerms(), currentTemplate, rules);
        contract.reviseCoffeeTerms(command.actorId(), CanonicalTerms.of(revised, canonicalizer));
        contractRepository.save(contract);
        log.info("Contract {} terms revised by {}, signedContentHash {}", command.contractId(), command.actorId(),
                contract.getCoffeeTerms().signedContentHash());
        return CoffeeContractView.from(contract);
    }
}
