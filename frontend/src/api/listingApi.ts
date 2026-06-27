import apiClient from './client'
import { env } from '../config/env'
import { MOCK_LISTINGS } from '../mocks/listings'
import type { CreateListingInput, Listing, ListingFilters } from '../types/listing'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

type BackendListing = Pick<
  Listing,
  | 'listingId'
  | 'productId'
  | 'sellerId'
  | 'productName'
  | 'quantity'
  | 'quantityUnit'
  | 'priceFloor'
  | 'currency'
  | 'deliveryDeadline'
  | 'status'
>

const toListing = (listing: BackendListing): Listing => ({
  ...listing,
  sellerName: '',
  category: '',
  description: '',
  location: '',
  qualityNotes: '',
  imageUrl: '',
})

const toBackendSort = (sortBy: ListingFilters['sortBy']) => {
  if (sortBy === 'price-asc') {
    return { sortBy: 'priceFloor', sortDirection: 'ASC' }
  }

  if (sortBy === 'price-desc') {
    return { sortBy: 'priceFloor', sortDirection: 'DESC' }
  }

  return { sortBy: 'createdAt', sortDirection: 'DESC' }
}

const applyFilters = (
  listings: Listing[],
  filters: ListingFilters = {
    categories: [],
  },
) => {
  let next = [...listings]

  if (filters.search) {
    const query = filters.search.toLowerCase()
    next = next.filter(
      (listing) =>
        listing.productName.toLowerCase().includes(query) ||
        listing.description.toLowerCase().includes(query) ||
        listing.sellerName.toLowerCase().includes(query),
    )
  }

  if (filters.categories.length > 0) {
    next = next.filter((listing) => filters.categories.includes(listing.category))
  }

  if (filters.maxPrice) {
    next = next.filter((listing) => listing.priceFloor <= filters.maxPrice!)
  }

  if (filters.deliveryWindow && filters.deliveryWindow !== 'all') {
    const windowDays =
      filters.deliveryWindow === '30d'
        ? 30
        : filters.deliveryWindow === '90d'
          ? 90
          : 365

    const today = new Date()
    next = next.filter((listing) => {
      const deadline = new Date(listing.deliveryDeadline)
      const diffDays =
        (deadline.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)

      return diffDays <= windowDays
    })
  }

  if (filters.sortBy === 'price-asc') {
    next.sort((a, b) => a.priceFloor - b.priceFloor)
  } else if (filters.sortBy === 'price-desc') {
    next.sort((a, b) => b.priceFloor - a.priceFloor)
  }

  return next
}

let mockListings = [...MOCK_LISTINGS]

export const listingApi = {
  async getAll(filters: ListingFilters) {
    if (env.useMocks) {
      await wait(300)
      return applyFilters(mockListings, filters)
    }

    const response = await apiClient.get('/api/v1/listings', {
      params: {
        page: 0,
        size: 100,
        ...toBackendSort(filters.sortBy),
      },
    })

    const listings = (response.data.data.content as BackendListing[]).map(toListing)
    return applyFilters(listings, filters)
  },

  async getById(listingId: string) {
    if (env.useMocks) {
      await wait(180)
      const listing = mockListings.find((item) => item.listingId === listingId)

      if (!listing) {
        throw new Error('Listing not found')
      }

      return listing
    }

    const response = await apiClient.get(`/api/v1/listings/${listingId}`)
    return toListing(response.data.data as BackendListing)
  },

  async create(input: CreateListingInput) {
    if (env.useMocks) {
      await wait(400)
      const listing: Listing = {
        listingId: `lst-${crypto.randomUUID().slice(0, 8)}`,
        productId: `prd-${crypto.randomUUID().slice(0, 8)}`,
        sellerId: 'seller-me',
        sellerName: 'Hợp tác xã Cà phê Đắk Lắk',
        productName: input.productName,
        category: input.category,
        description: input.description,
        quantity: input.quantity,
        quantityUnit: input.quantityUnit,
        priceFloor: input.priceFloor,
        currency: 'VND',
        deliveryDeadline: input.deliveryDeadline,
        status: 'ACTIVE',
        location: input.location,
        qualityNotes: input.qualityNotes,
        imageUrl:
          'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&q=80',
      }

      mockListings = [listing, ...mockListings]
      return listing
    }

    const response = await apiClient.post('/api/v1/listings', input)
    return response.data.data as Listing
  },
}
