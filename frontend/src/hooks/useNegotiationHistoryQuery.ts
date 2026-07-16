import { useQuery } from '@tanstack/react-query'

import { contractApi } from '../api/contractApi'

export const useNegotiationHistoryQuery = (contractId: string, enabled: boolean) =>
  useQuery({
    queryKey: ['contract-negotiations', contractId],
    queryFn: () => contractApi.getNegotiationHistory(contractId),
    enabled: Boolean(contractId) && enabled,
    staleTime: 10_000,
    retry: false,
  })
