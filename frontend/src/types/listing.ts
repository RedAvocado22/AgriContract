export type ListingStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED'

export interface Listing {
  listingId: string
  productId: string
  sellerId: string
  sellerName: string
  productName: string
  category: string
  description: string
  quantity: number
  quantityUnit: string
  priceFloor: number
  currency: string
  deliveryDeadline: string
  status: ListingStatus
  location: string
  qualityNotes: string
  imageUrl: string
}

export interface ListingFilters {
  categories: string[]
  minPrice?: number
  maxPrice?: number
  deliveryWindow?: string
  search?: string
  sortBy?: 'latest' | 'price-asc' | 'price-desc'
}

export interface CreateListingInput {
  productId: string
  quantity: number
  quantityUnit: string
  priceFloor: number
  deliveryDeadline: string
}
