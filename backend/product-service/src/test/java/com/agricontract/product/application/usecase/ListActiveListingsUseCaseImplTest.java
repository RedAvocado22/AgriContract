package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingSearchItem;
import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchResult;
import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.ReputationStatus;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.dto.SellerReputation;
import com.agricontract.product.application.port.SellerReputationPort;
import com.agricontract.product.common.exception.ReputationSignInRequiredException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListActiveListingsUseCaseImplTest {
    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final ReputationViewer BUYER = new ReputationViewer("buyer-1", List.of("BUYER"));

    @Mock private ListingRepository listingRepository;
    @Mock private SellerReputationPort sellerReputationPort;
    @InjectMocks private ListActiveListingsUseCaseImpl useCase;

    private static Listing listing(String id, String sellerId) {
        return Listing.create(new ListingId(id), sellerId, new ProductId("product-1"), "Ca phe Robusta", "",
                new Quantity(new BigDecimal("100"), "kg"), null, new Money(new BigDecimal("50000"), "VND"),
                LocalDate.of(2026, 12, 31), PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods());
    }

    private void page(Listing... listings) {
        when(listingRepository.findByStatus(ListingStatus.ACTIVE, PAGE)).thenReturn(List.of(listings));
        when(listingRepository.countByStatus(ListingStatus.ACTIVE)).thenReturn(45L);
    }

    private static List<String> ids(ListingSearchResult result) {
        return result.page().content().stream().map(item -> item.listing().listingId()).toList();
    }

    @Test
    void anonymous_getsListingsWithoutReputation_andNoLookup() {
        page(listing("l-1", "seller-a"));

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, null, null));

        assertThat(ids(result)).containsExactly("l-1");
        assertThat(result.page().content().getFirst().sellerReputation()).isNull();
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.SIGN_IN_REQUIRED);
        verifyNoInteractions(sellerReputationPort);
    }

    @Test
    void anonymous_withReputationFilter_isRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> useCase.execute(new ListingSearchQuery(PAGE, 70, null)))
                .isInstanceOf(ReputationSignInRequiredException.class);
        verifyNoInteractions(listingRepository, sellerReputationPort);
    }

    @Test
    void signedIn_readsEachSellerOnce_andShowsReputation() {
        page(listing("l-1", "seller-a"), listing("l-2", "seller-b"), listing("l-3", "seller-a"));
        when(sellerReputationPort.lookup(Set.of("seller-a", "seller-b"))).thenReturn(new ReputationLookup(
                Map.of("seller-a", new SellerReputation(90, false), "seller-b", new SellerReputation(null, false)),
                true));

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, null, BUYER));

        assertThat(ids(result)).containsExactly("l-1", "l-2", "l-3");
        assertThat(result.page().content()).extracting(ListingSearchItem::sellerReputation).containsExactly(
                new SellerReputation(90, false), new SellerReputation(null, false), new SellerReputation(90, false));
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.AVAILABLE);
        assertThat(result.page().totalElements()).isEqualTo(45);
        verify(sellerReputationPort, times(1)).lookup(any());
    }

    @Test
    void minSellerScore_dropsOnlyKnownScoresBelowIt_unratedPasses() {
        page(listing("low", "seller-low"), listing("edge", "seller-edge"), listing("new", "seller-new"),
                listing("high", "seller-high"));
        when(sellerReputationPort.lookup(any())).thenReturn(new ReputationLookup(Map.of(
                "seller-low", new SellerReputation(69, false),
                "seller-edge", new SellerReputation(70, false),
                "seller-new", new SellerReputation(null, false),
                "seller-high", new SellerReputation(100, true)), true));

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, 70, BUYER));

        assertThat(ids(result)).containsExactly("edge", "new", "high");
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.AVAILABLE);
        // the filter runs on the page that was read: totals still count the unfiltered ACTIVE listings
        assertThat(result.page().totalElements()).isEqualTo(45);
    }

    @Test
    void reputationOutage_keepsEveryListing_andFlagsUnavailable() {
        page(listing("l-1", "seller-a"), listing("l-2", "seller-b"));
        when(sellerReputationPort.lookup(any())).thenReturn(new ReputationLookup(Map.of(), false));

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, 90, BUYER));

        assertThat(ids(result)).containsExactly("l-1", "l-2");
        assertThat(result.page().content()).extracting(ListingSearchItem::sellerReputation).containsOnlyNulls();
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.UNAVAILABLE);
    }

    @Test
    void partialOutage_filtersKnownSellers_keepsUnknownOnes() {
        page(listing("known-low", "seller-a"), listing("unknown", "seller-b"));
        when(sellerReputationPort.lookup(any())).thenReturn(
                new ReputationLookup(Map.of("seller-a", new SellerReputation(10, false)), false));

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, 50, BUYER));

        assertThat(ids(result)).containsExactly("unknown");
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.UNAVAILABLE);
    }

    @Test
    void emptyPage_doesNotCallReputation() {
        page();

        ListingSearchResult result = useCase.execute(new ListingSearchQuery(PAGE, 50, BUYER));

        assertThat(result.page().content()).isEmpty();
        assertThat(result.reputationStatus()).isEqualTo(ReputationStatus.AVAILABLE);
        verifyNoInteractions(sellerReputationPort);
    }
}
