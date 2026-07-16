import { useQuery } from '@tanstack/react-query'

import { listingApi } from '../api/listingApi'

export const useSellerListingsQuery = () =>
  useQuery({
    queryKey: ['seller-listings'],
    queryFn: listingApi.getSellerListings,
  })
