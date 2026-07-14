import { useQuery } from '@tanstack/react-query'

import { productApi } from '../api/productApi'

export const useProductsQuery = () =>
  useQuery({
    queryKey: ['products'],
    queryFn: () => productApi.getAll(),
  })
