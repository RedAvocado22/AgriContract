const CATEGORY_LABELS: Record<string, string> = {
  GRAIN: 'Lúa gạo',
  COFFEE: 'Cà phê',
  SPICE: 'Gia vị',
  FRUIT: 'Trái cây',
}

export const formatProductCategory = (category: string) =>
  CATEGORY_LABELS[category] ?? category
