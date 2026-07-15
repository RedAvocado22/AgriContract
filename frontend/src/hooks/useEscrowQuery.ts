import { useQuery } from '@tanstack/react-query'

import { escrowApi } from '../api/escrowApi'

export const useEscrowByContractQuery = (contractId: string) =>
  useQuery({
    queryKey: ['escrow', contractId],
    queryFn: () => escrowApi.getByContractId(contractId),
    enabled: Boolean(contractId),
    retry: false,
  })

export const useEscrowTransactionsQuery = (escrowId: string | undefined) =>
  useQuery({
    queryKey: ['escrow-transactions', escrowId],
    queryFn: () => escrowApi.getTransactions(escrowId!),
    enabled: Boolean(escrowId),
  })
