import { useQuery } from '@tanstack/react-query'

import { contractApi } from '../api/contractApi'

export const useContractsQuery = (role?: string) =>
  useQuery({
    queryKey: ['contracts', role],
    queryFn: () => contractApi.getAll(role),
  })
