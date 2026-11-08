import { apiClient } from "@/shared/api/client"

export interface MyProfile {
  userId: string
  organizationName: string
}

export async function getMyProfile(signal?: AbortSignal): Promise<MyProfile> {
  const { data } = await apiClient.get<MyProfile>("/api/v1/users/me", {
    signal,
  })
  if (
    !data ||
    typeof data.userId !== "string" ||
    typeof data.organizationName !== "string"
  ) {
    throw new Error("Phản hồi hồ sơ không đúng định dạng.")
  }
  return data
}
