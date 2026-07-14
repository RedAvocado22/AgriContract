export interface Product {
  productId: string
  name: string
  unit: string
  category: string
}

export interface CreateProductInput {
  name: string
  unit: string
  category: string
}
