import { api } from '@/api/client'
import type { RoleCode, UserCreatePayload, UserResponse } from '@/types/user'

export async function fetchUsers(roleName?: RoleCode): Promise<UserResponse[]> {
  const { data } = await api.get<UserResponse[]>('/api/users', {
    params: roleName ? { roleName } : undefined,
  })
  return data
}

export async function createUser(payload: UserCreatePayload): Promise<UserResponse> {
  const { data } = await api.post<UserResponse>('/api/users', payload)
  return data
}

export async function updateUserRole(
  userId: string,
  roleName: RoleCode,
  lineId?: string | null,
): Promise<UserResponse> {
  const { data } = await api.patch<UserResponse>(
    `/api/users/${encodeURIComponent(userId)}/role`,
    { roleName, lineId },
  )
  return data
}

export async function updateUserLock(userId: string, locked: boolean): Promise<UserResponse> {
  const { data } = await api.patch<UserResponse>(
    `/api/users/${encodeURIComponent(userId)}/lock`,
    { locked },
  )
  return data
}
