export interface Product {
  productId: string
  name: string
  unit: string
  categoryId: string
  images: string[]
  category?: string
}

export interface CreateProductInput {
  name: string
  unit: string
  category: string
  imageUrls?: string[]
}
