package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListActiveListingsUseCaseImpl implements ListActiveListingsUseCase {
    private final ListingRepository listingRepository;

    @Override
    public PagedResult<ListingResponse> execute(Pageable pageable) {
        List<ListingResponse> content = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable)
                .stream().map(ListingResponse::from).toList();
        long total = listingRepository.countByStatus(ListingStatus.ACTIVE);
        return new PagedResult<>(content, total, (int) Math.ceil((double) total / pageable.getPageSize()));
    }
}
