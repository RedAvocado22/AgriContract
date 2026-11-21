import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useAuthStore } from "@/shared/auth/auth-store"
import { getContract, listContracts } from "../api/contracts"
import { pollInterval } from "../types/presentation"
import type { ParticipantRole } from "../types/contract"

export function useContracts(
  role: ParticipantRole,
  status: string,
  page: number
) {
  const { authenticated, status: authStatus, userId, roles } = useAuthStore()
  return useQuery({
    queryKey: ["contracts", userId, role, status, page],
    queryFn: ({ signal }) => listContracts(userId!, role, status, page, signal),
    enabled:
      authenticated &&
      authStatus === "ready" &&
      !!userId &&
      roles.includes(role),
    retry: false,
  })
}
export function useContract(id: string) {
  const { authenticated, status, userId } = useAuthStore()
  const [startedAt] = useState(Date.now)
  return useQuery({
    queryKey: ["contract", userId, id],
    queryFn: ({ signal }) => getContract(id, userId!, signal),
    enabled: authenticated && status === "ready" && !!userId && !!id,
    retry: false,
    refetchOnWindowFocus: false,
    refetchIntervalInBackground: false,
    refetchInterval: (query) =>
      pollInterval(
        query.state.data?.status,
        startedAt,
        Date.now(),
        !!query.state.error
      ),
  })
}
