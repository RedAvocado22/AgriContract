package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSellerListingsUseCaseImpl implements GetSellerListingsUseCase {

    private final ListingRepository listingRepository;

    @Override
    public PagedResult<ListingResponse> execute(String sellerId, Pageable pageable) {
        List<ListingResponse> content = listingRepository.findBySellerId(sellerId, pageable)
                .stream().map(ListingResponse::from).toList();
        long total = listingRepository.countBySellerId(sellerId);
        return new PagedResult<>(content, total, (int) Math.ceil((double) total / pageable.getPageSize()));
    }
}
