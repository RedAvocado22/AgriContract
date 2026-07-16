import apiClient from './client'
import { env } from '../config/env'
import { MOCK_PRODUCTS } from '../mocks/products'
import type { CreateProductInput, Product } from '../types/product'
import {
  GENERIC_PRODUCT_IMAGE,
  getGenericProductImageUrlForApi,
  normalizeProductCategory,
} from '../utils/productCategory'
import { repairMojibake } from '../utils/textEncoding'

type BackendProduct = Product
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const MARKETPLACE_TIMEOUT_MS = 2500

const toProduct = (product: BackendProduct): Product => ({
  ...product,
  name: repairMojibake(product.name),
  categoryId: product.categoryId ?? product.category ?? 'OTHER',
  category: product.category ?? product.categoryId ?? 'OTHER',
  images: product.images?.length ? product.images : [GENERIC_PRODUCT_IMAGE],
})

export const productApi = {
  async getAll() {
    if (env.useMocks) {
      await wait(180)
      return MOCK_PRODUCTS
    }

    const response = await apiClient.get('/api/v1/products', {
      params: { page: 0, size: 100 },
      timeout: MARKETPLACE_TIMEOUT_MS,
    })
    return (response.data.data.content as BackendProduct[]).map(toProduct)
  },

  async create(input: CreateProductInput) {
    if (env.useMocks) {
      await wait(220)
      const product: Product = {
        productId: `prd-${crypto.randomUUID().slice(0, 8)}`,
        name: input.name,
        unit: input.unit,
        categoryId: normalizeProductCategory(input.category),
        category: normalizeProductCategory(input.category),
        images: input.imageUrls ?? [GENERIC_PRODUCT_IMAGE],
      }

      MOCK_PRODUCTS.unshift(product)
      return product
    }

    const categoryId = normalizeProductCategory(input.category)
    const response = await apiClient.post('/api/v1/products', {
      name: input.name,
      unit: input.unit,
      categoryId,
      imageUrls: input.imageUrls ?? [getGenericProductImageUrlForApi()],
    })
    return toProduct(response.data.data as BackendProduct)
  },
}
