package com.agricontract.product.domain.model.vo;

// Phase 2 accepts coffee only (design §1/§9): a category can be approved only as COFFEE, so a product or listing
// in scope always traces to COFFEE. Categories approved before this field existed have no commodity.
public enum Commodity {
    COFFEE
}
