import apiClient from './client'
import { env } from '../config/env'
import { MOCK_PRODUCTS } from '../mocks/products'
import type { CreateProductInput, Product } from '../types/product'

type BackendProduct = Product
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export const productApi = {
  async getAll() {
    if (env.useMocks) {
      await wait(180)
      return MOCK_PRODUCTS
    }

    const response = await apiClient.get('/api/v1/products', {
      params: {
        page: 0,
        size: 100,
      },
    })

    return response.data.data.content as BackendProduct[]
  },

  async create(input: CreateProductInput) {
    if (env.useMocks) {
      await wait(220)
      const product: Product = {
        productId: `prd-${crypto.randomUUID().slice(0, 8)}`,
        name: input.name,
        unit: input.unit,
        category: input.category,
      }

      MOCK_PRODUCTS.unshift(product)
      return product
    }

    const response = await apiClient.post('/api/v1/products', input)
    return response.data.data as Product
  },
}
