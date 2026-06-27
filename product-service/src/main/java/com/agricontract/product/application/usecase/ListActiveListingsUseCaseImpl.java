package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListActiveListingsUseCaseImpl implements ListActiveListingsUseCase {
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;

    @Override
    public Page<ListingResponse> execute(Pageable pageable) {
        Page<Listing> listing = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable);

        return listing.map(listingMapper::toResponse);
    }
}
