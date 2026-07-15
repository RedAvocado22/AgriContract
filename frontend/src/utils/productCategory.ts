export const PRODUCT_CATEGORIES = [
  { value: 'RICE', label: 'Lúa gạo và ngũ cốc', icon: 'rice_bowl' },
  { value: 'COFFEE', label: 'Cà phê', icon: 'local_cafe' },
  { value: 'CASHEW', label: 'Hạt điều', icon: 'nutrition' },
  { value: 'RUBBER', label: 'Cao su', icon: 'forest' },
  { value: 'FRUIT', label: 'Trái cây', icon: 'psychiatry' },
  { value: 'VEGETABLE', label: 'Rau củ', icon: 'eco' },
  { value: 'SPICE', label: 'Gia vị', icon: 'spa' },
  { value: 'OTHER', label: 'Khác', icon: 'inventory_2' },
] as const

export const GENERIC_PRODUCT_IMAGE =
  '/no-product-image.svg'

export const getGenericProductImageUrlForApi = () => {
  if (typeof window === 'undefined') {
    return GENERIC_PRODUCT_IMAGE
  }

  return new URL(GENERIC_PRODUCT_IMAGE, window.location.origin).href
}

const CATEGORY_LABELS = PRODUCT_CATEGORIES.reduce<Record<string, string>>((acc, category) => {
  acc[category.value] = category.label
  return acc
}, {})

const LEGACY_CATEGORY_MAP: Record<string, string> = {
  GRAIN: 'RICE',
  'Lua gao': 'RICE',
  'Ca phe': 'COFFEE',
  Dieu: 'CASHEW',
  'Cao su': 'RUBBER',
  'Trai cay': 'FRUIT',
  'Rau qua': 'VEGETABLE',
  'Gia vi': 'SPICE',
}

export const normalizeProductCategory = (category: string | undefined) => {
  if (!category) {
    return 'OTHER'
  }

  const trimmed = category.trim()
  const direct = PRODUCT_CATEGORIES.find(
    (item) =>
      item.value === trimmed.toUpperCase() ||
      item.label.toLowerCase() === trimmed.toLowerCase(),
  )

  return direct?.value ?? LEGACY_CATEGORY_MAP[trimmed] ?? trimmed.toUpperCase().replace(/\s+/g, '_')
}

export const formatProductCategory = (category: string | undefined) => {
  const normalized = normalizeProductCategory(category)
  return CATEGORY_LABELS[normalized] ?? category ?? 'Khác'
}

export const getCategoryImage = (category: string | undefined) => {
  const normalized = normalizeProductCategory(category)

  const images: Record<string, string> = {
    RICE: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=1200&q=80',
    COFFEE:
      'https://images.unsplash.com/photo-1447933601403-0c6688de566e?auto=format&fit=crop&w=1200&q=80',
    CASHEW:
      'https://images.unsplash.com/photo-1508747703725-719777637510?auto=format&fit=crop&w=1200&q=80',
    RUBBER:
      'https://images.unsplash.com/photo-1471193945509-9ad0617afabf?auto=format&fit=crop&w=1200&q=80',
    FRUIT:
      'https://images.unsplash.com/photo-1619566636858-adf3ef46400b?auto=format&fit=crop&w=1200&q=80',
    VEGETABLE:
      'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=1200&q=80',
    SPICE:
      'https://images.unsplash.com/photo-1532336414038-cf19250c5757?auto=format&fit=crop&w=1200&q=80',
    OTHER: GENERIC_PRODUCT_IMAGE,
  }

  return images[normalized] ?? GENERIC_PRODUCT_IMAGE
}
