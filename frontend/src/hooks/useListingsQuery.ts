import { useQuery } from '@tanstack/react-query'

import { applyListingFilters, listingApi } from '../api/listingApi'
import type { ListingFilters } from '../types/listing'

export const useListingsQuery = (filters: ListingFilters) =>
  useQuery({
    queryKey: ['listings', filters.sortBy],
    queryFn: () => listingApi.getAll({
      categories: [],
      minPrice: undefined,
      maxPrice: undefined,
      deliveryWindow: 'all',
      search: '',
      sortBy: filters.sortBy,
    }),
    select: (listings) => applyListingFilters(listings, filters),
    staleTime: 30_000,
  })
