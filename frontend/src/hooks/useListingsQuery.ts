import { useQuery } from '@tanstack/react-query'

import { listingApi } from '../api/listingApi'
import type { ListingFilters } from '../types/listing'

export const useListingsQuery = (filters: ListingFilters) =>
  useQuery({
    queryKey: ['listings', filters],
    queryFn: () => listingApi.getAll(filters),
  })
