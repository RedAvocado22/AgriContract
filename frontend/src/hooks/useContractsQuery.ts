import { useQuery } from '@tanstack/react-query'

import { contractApi } from '../api/contractApi'

export const useContractsQuery = (role?: string) =>
  useQuery({
    queryKey: ['contracts', role],
    queryFn: () => contractApi.getAll(role),
    refetchInterval: (query) =>
      query.state.data?.some((contract) =>
        ['OFFERED', 'NEGOTIATING', 'SIGNED', 'DELIVERED'].includes(contract.status),
      )
        ? 10_000
        : false,
  })
