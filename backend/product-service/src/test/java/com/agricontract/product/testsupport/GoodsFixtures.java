package com.agricontract.product.testsupport;

import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.CoffeeType;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.domain.model.vo.ListingGoods;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GoodsFixtures {

    private GoodsFixtures() {
    }

    public static CoffeeQualitySpec robusta() {
        return new CoffeeQualitySpec(CoffeeType.ROBUSTA, new BigDecimal("12.5"), new BigDecimal("0.5"),
                new BigDecimal("5.0"));
    }

    public static ListingGoods coffeeGoods() {
        return new ListingGoods(new CategoryId("category-1"), Commodity.COFFEE, 2026, "Nien vu 2026/27",
                List.of("4C (cam ket)"), "Buon Ma Thuot, Dak Lak", robusta(), "Bao day 60 kg",
                "Kho HTX Ea Kar", LocalDate.of(2026, 12, 1), null);
    }
}
