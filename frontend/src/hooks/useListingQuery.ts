import { useQuery } from '@tanstack/react-query'

import { listingApi } from '../api/listingApi'

export const useListingQuery = (listingId: string) =>
  useQuery({
    queryKey: ['listing', listingId],
    queryFn: () => listingApi.getById(listingId),
    enabled: Boolean(listingId),
  })
