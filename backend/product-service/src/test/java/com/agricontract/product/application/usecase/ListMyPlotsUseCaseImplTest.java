package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotRegistryPage;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.PageQuery;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListMyPlotsUseCaseImplTest {

    private final PlotRegistryRepository repository = mock(PlotRegistryRepository.class);
    private final ListMyPlotsUseCaseImpl useCase = new ListMyPlotsUseCaseImpl(repository);

    @Test
    void readsOnlyTheCallersEntries_andCountsPages() {
        PageQuery page = PageQuery.of(1, 20);
        PlotRegistryEntry point = PlotRegistryEntry.register(new RegistryEntryId("plot-1"), "seller-1", "Ho A",
                PlotFixtures.point(), PlotSource.MANUAL_PIN, new BigDecimal("8.5"), Province.DAK_LAK,
                Instant.parse("2026-10-24T03:00:00Z"));
        when(repository.findBySellerIdNewestFirst("seller-1", page)).thenReturn(List.of(point));
        when(repository.countBySellerId("seller-1")).thenReturn(21L);

        PlotRegistryPage result = useCase.execute("seller-1", page);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.registryEntryId()).isEqualTo("plot-1");
            // the contract gives a POINT entry no area
            assertThat(item.areaHectares()).isNull();
        });
        verify(repository).findBySellerIdNewestFirst("seller-1", page);
        verify(repository).countBySellerId("seller-1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noEntries_isEmptyPageWithZeroPages() {
        when(repository.findBySellerIdNewestFirst(any(), any())).thenReturn(List.of());
        when(repository.countBySellerId("seller-1")).thenReturn(0L);

        PlotRegistryPage result = useCase.execute("seller-1", PageQuery.of(0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
    }
}
