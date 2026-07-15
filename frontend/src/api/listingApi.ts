import apiClient from './client'
import { env } from '../config/env'
import { MOCK_LISTINGS } from '../mocks/listings'
import { MOCK_PRODUCTS } from '../mocks/products'
import type { CreateListingInput, Listing, ListingFilters } from '../types/listing'
import type { Product } from '../types/product'
import { getLocalListingImages } from '../utils/localListingImages'
import { formatProductCategory, GENERIC_PRODUCT_IMAGE, getCategoryImage } from '../utils/productCategory'
import { repairMojibake } from '../utils/textEncoding'
import { productApi } from './productApi'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const MARKETPLACE_TIMEOUT_MS = 2500

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
> & {
  coverImageUrl?: string
}

const sellerNameFallback = (sellerId: string) =>
  sellerId ? `Bên bán ${sellerId.slice(0, 8)}` : 'Bên bán đã xác minh'

const withLocalImages = (listing: Listing): Listing => {
  const localImages = getLocalListingImages(listing.listingId)

  if (localImages.length === 0) {
    return listing
  }

  return {
    ...listing,
    imageUrl: localImages[0],
    imageUrls: localImages,
  }
}

const toListing = (listing: BackendListing, products: Product[] = []): Listing => {
  const product = products.find((item) => item.productId === listing.productId)
  const productName = repairMojibake(listing.productName)
  const productCategory = product?.categoryId ?? product?.category
  const category = formatProductCategory(productCategory)
  const unit = listing.quantityUnit || product?.unit || 'đơn vị'
  const backendImages = product?.images?.length ? product.images : []
  const coverImage = listing.coverImageUrl || backendImages[0] || GENERIC_PRODUCT_IMAGE

  return withLocalImages({
    ...listing,
    productName,
    category,
    quantityUnit: unit,
    sellerName: sellerNameFallback(listing.sellerId),
    description: `${productName} sẵn sàng thương lượng hợp đồng với thanh toán ký quỹ bảo chứng.`,
    location: 'Chưa xác nhận',
    qualityNotes: 'Hai bên có thể chốt quy cách chất lượng khi thương lượng hợp đồng.',
    imageUrl: coverImage,
    imageUrls: listing.coverImageUrl ? [listing.coverImageUrl, ...backendImages.slice(1)] : backendImages,
  })
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
      return applyFilters(mockListings.map(withLocalImages), filters)
    }

    try {
      const [response, products] = await Promise.all([
        apiClient.get('/api/v1/listings', {
          params: {
            page: 0,
            size: 100,
            ...toBackendSort(filters.sortBy),
          },
          timeout: MARKETPLACE_TIMEOUT_MS,
        }),
        productApi.getAll(),
      ])

      const listingsContent = response.data.data.content as BackendListing[]
      return applyFilters(
        listingsContent.map((listing) => toListing(listing, products)),
        filters,
      )
    } catch {
      return applyFilters(mockListings.map(withLocalImages), filters)
    }
  },

  async getById(listingId: string) {
    if (env.useMocks) {
      await wait(180)
      const listing = mockListings.find((item) => item.listingId === listingId)

      if (!listing) {
        throw new Error('Không tìm thấy tin hàng')
      }

      return withLocalImages(listing)
    }

    try {
      const [listingResponse, products] = await Promise.all([
        apiClient.get(`/api/v1/listings/${listingId}`, {
          timeout: MARKETPLACE_TIMEOUT_MS,
        }),
        productApi.getAll(),
      ])

      return toListing(listingResponse.data.data as BackendListing, products)
    } catch {
      const listing = mockListings.find((item) => item.listingId === listingId)

      if (!listing) {
        throw new Error('Không tìm thấy tin hàng')
      }

      return withLocalImages(listing)
    }
  },

  async create(input: CreateListingInput) {
    if (env.useMocks) {
      await wait(400)
      const product = MOCK_PRODUCTS.find((item) => item.productId === input.productId)
      const imageUrls = product?.images?.length
        ? product.images
        : [getCategoryImage(product?.categoryId ?? product?.category)]
      const listing: Listing = {
        listingId: `lst-${crypto.randomUUID().slice(0, 8)}`,
        productId: input.productId,
        sellerId: 'seller-me',
        sellerName: 'HTX Cà phê Đắk Lắk',
        productName: product?.name ?? 'Sản phẩm mới',
        category: formatProductCategory(product?.categoryId ?? product?.category),
        description: 'Nguồn hàng mới đăng, sẵn sàng thương lượng với bên mua.',
        quantity: input.quantity,
        quantityUnit: input.quantityUnit,
        priceFloor: input.priceFloor,
        currency: input.currency ?? 'VND',
        deliveryDeadline: input.deliveryDeadline,
        status: 'ACTIVE',
        location: 'Chưa xác nhận',
        qualityNotes: 'Bổ sung quy cách chất lượng cụ thể trong đề nghị hợp đồng.',
        imageUrl: imageUrls[0],
        imageUrls,
      }

      mockListings = [listing, ...mockListings]
      return withLocalImages(listing)
    }

    const response = await apiClient.post('/api/v1/listings', {
      ...input,
      currency: input.currency ?? 'VND',
    })
    return toListing(response.data.data as BackendListing, await productApi.getAll())
  },
}
