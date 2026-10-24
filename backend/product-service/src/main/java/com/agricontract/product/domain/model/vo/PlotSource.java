package com.agricontract.product.domain.model.vo;

public enum PlotSource {
    KML_IMPORT,
    // browser geolocation pin; POINT only, a hand-placed polygon is too imprecise (design §2.2)
    MANUAL_PIN
}
