import type { Product } from '../types/product'
import { getCategoryImage } from '../utils/productCategory'

export const MOCK_PRODUCTS: Product[] = [
  {
    productId: 'prd-001',
    name: 'Cà phê Robusta Đắk Lắk',
    unit: 'tấn',
    categoryId: 'COFFEE',
    category: 'COFFEE',
    images: [getCategoryImage('COFFEE')],
  },
  {
    productId: 'prd-002',
    name: 'Gạo ST25 An Giang',
    unit: 'tấn',
    categoryId: 'RICE',
    category: 'RICE',
    images: [getCategoryImage('RICE')],
  },
  {
    productId: 'prd-003',
    name: 'Điều thô Bình Phước',
    unit: 'tấn',
    categoryId: 'CASHEW',
    category: 'CASHEW',
    images: [getCategoryImage('CASHEW')],
  },
  {
    productId: 'prd-004',
    name: 'Mủ cao su thiên nhiên Tây Ninh',
    unit: 'tấn',
    categoryId: 'RUBBER',
    category: 'RUBBER',
    images: [getCategoryImage('RUBBER')],
  },
]
