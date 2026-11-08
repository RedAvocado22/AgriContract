import { useQuery } from "@tanstack/react-query"
import { useAuthStore } from "@/shared/auth/auth-store"
import { getMyProfile } from "../api/profile"

export function useMyProfile() {
  const userId = useAuthStore((state) => state.userId)
  const authenticated = useAuthStore((state) => state.authenticated)
  return useQuery({
    queryKey: ["my-profile", userId],
    queryFn: ({ signal }) => getMyProfile(signal),
    enabled: authenticated && Boolean(userId),
  })
}
