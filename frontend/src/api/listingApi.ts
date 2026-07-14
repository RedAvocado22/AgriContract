import apiClient from './client'
import { env } from '../config/env'
import { MOCK_LISTINGS } from '../mocks/listings'
import { MOCK_PRODUCTS } from '../mocks/products'
import type { CreateListingInput, Listing, ListingFilters } from '../types/listing'
import type { Product } from '../types/product'
import { formatProductCategory, getCategoryImage } from '../utils/productCategory'
import { productApi } from './productApi'

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

const sellerNameFallback = (sellerId: string) =>
  sellerId ? `Seller ${sellerId.slice(0, 8)}` : 'Verified seller'

const toListing = (listing: BackendListing, products: Product[] = []): Listing => {
  const product = products.find((item) => item.productId === listing.productId)
  const category = formatProductCategory(product?.category)
  const unit = listing.quantityUnit || product?.unit || 'unit'

  return {
    ...listing,
    category,
    quantityUnit: unit,
    sellerName: sellerNameFallback(listing.sellerId),
    description: `${listing.productName} available for contract negotiation with escrow-backed settlement.`,
    location: 'To be confirmed',
    qualityNotes: 'Quality specification can be finalized during contract negotiation.',
    imageUrl: getCategoryImage(product?.category),
  }
}

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
        listing.sellerName.toLowerCase().includes(query) ||
        listing.category.toLowerCase().includes(query),
    )
  }

  if (filters.categories.length > 0) {
    next = next.filter((listing) => filters.categories.includes(listing.category))
  }

  if (filters.minPrice !== undefined) {
    next = next.filter((listing) => listing.priceFloor >= filters.minPrice!)
  }

  if (filters.maxPrice !== undefined) {
    next = next.filter((listing) => listing.priceFloor <= filters.maxPrice!)
  }

  if (filters.deliveryWindow && filters.deliveryWindow !== 'all') {
    const windowDays =
      filters.deliveryWindow === '30d' ? 30 : filters.deliveryWindow === '90d' ? 90 : 365

    const today = new Date()
    next = next.filter((listing) => {
      const deadline = new Date(listing.deliveryDeadline)
      const diffDays = (deadline.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)

      return diffDays >= 0 && diffDays <= windowDays
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

    const [response, products] = await Promise.all([
      apiClient.get('/api/v1/listings', {
        params: {
          page: 0,
          size: 100,
          ...toBackendSort(filters.sortBy),
        },
      }),
      productApi.getAll(),
    ])

    const listingsContent = response.data.data.content as BackendListing[]
    return applyFilters(
      listingsContent.map((listing) => toListing(listing, products)),
      filters,
    )
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

    const [listingResponse, products] = await Promise.all([
      apiClient.get(`/api/v1/listings/${listingId}`),
      productApi.getAll(),
    ])

    return toListing(listingResponse.data.data as BackendListing, products)
  },

  async create(input: CreateListingInput) {
    if (env.useMocks) {
      await wait(400)
      const product = MOCK_PRODUCTS.find((item) => item.productId === input.productId)
      const listing: Listing = {
        listingId: `lst-${crypto.randomUUID().slice(0, 8)}`,
        productId: input.productId,
        sellerId: 'seller-me',
        sellerName: 'Dak Lak Coffee Cooperative',
        productName: product?.name ?? 'New product',
        category: formatProductCategory(product?.category),
        description: 'Newly posted supply ready for buyer negotiation.',
        quantity: input.quantity,
        quantityUnit: input.quantityUnit,
        priceFloor: input.priceFloor,
        currency: input.currency ?? 'VND',
        deliveryDeadline: input.deliveryDeadline,
        status: 'ACTIVE',
        location: 'To be confirmed',
        qualityNotes: 'Add exact quality terms in the contract proposal.',
        imageUrl: getCategoryImage(product?.category),
      }

      mockListings = [listing, ...mockListings]
      return listing
    }

    const response = await apiClient.post('/api/v1/listings', {
      ...input,
      currency: input.currency ?? 'VND',
    })
    return toListing(response.data.data as BackendListing, await productApi.getAll())
  },
}
