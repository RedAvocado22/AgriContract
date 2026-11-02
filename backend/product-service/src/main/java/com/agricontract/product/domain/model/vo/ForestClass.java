package com.agricontract.product.domain.model.vo;

// Class of a 2020 forest-baseline area, as the dataset labels it (design §2.2.4). Tree crops are not stored:
// ground outside every area of a covered extent is agricultural land use.
public enum ForestClass {
    NATURAL_FOREST,
    // the dataset could not separate forest from tree crops here (multi-layer intercropping, class boundary)
    AMBIGUOUS
}
