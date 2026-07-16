import { useQuery } from '@tanstack/react-query'

import { contractApi } from '../api/contractApi'

export const useContractQuery = (contractId: string) =>
  useQuery({
    queryKey: ['contract', contractId],
    queryFn: () => contractApi.getById(contractId),
    enabled: Boolean(contractId),
    refetchInterval: (query) => {
      if (['SIGNED', 'DELIVERED'].includes(query.state.data?.status ?? '')) return 3000
      return ['OFFERED', 'NEGOTIATING'].includes(query.state.data?.status ?? '') ? 10_000 : false
    },
  })
