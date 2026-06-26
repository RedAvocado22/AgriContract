package com.agricontract.product.application.usecase;

import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloseListingUseCaseImpl implements CloseListingUseCase {
    private final ListingRepository listingRepository;

    @Override
    @Transactional
    public void execute(String listingId) {
        Listing listing = listingRepository.findById(new ListingId(listingId)).orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.close();
        listingRepository.save(listing);
    }
}
