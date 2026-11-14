package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotRegistryItem;
import com.agricontract.product.application.dto.PlotRegistryPage;
import com.agricontract.product.domain.repository.PageQuery;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Design §4: only the caller's own entries; sellerId always comes from the authenticated principal.
@Service
@RequiredArgsConstructor
public class ListMyPlotsUseCaseImpl implements ListMyPlotsUseCase {

    private final PlotRegistryRepository plotRegistryRepository;

    @Override
    public PlotRegistryPage execute(String sellerId, PageQuery page) {
        List<PlotRegistryItem> content = plotRegistryRepository.findBySellerIdNewestFirst(sellerId, page)
                .stream().map(PlotRegistryItem::from).toList();
        long total = plotRegistryRepository.countBySellerId(sellerId);
        return new PlotRegistryPage(content, page.page(), page.size(), total,
                (int) ((total + page.size() - 1) / page.size()));
    }
}
