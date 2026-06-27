package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetSellerListingsUseCaseImpl implements GetSellerListingsUseCase {

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;

    @Override
    public Page<ListingResponse> execute(String sellerId, Pageable pageable) {
        return listingRepository.findBySellerId(sellerId, pageable)
                .map(listingMapper::toResponse);
    }
}
