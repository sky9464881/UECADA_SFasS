import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  createUser,
  fetchUsers,
  updateUserLock,
  updateUserRole,
} from '@/api/userApi'
import type {
  RoleCode,
  UserCreatePayload,
  UserResponse,
} from '@/types/user'
import { STALE_TIME_MS } from '@/constants/polling'

export interface UseUsersOptions {
  roleName?: Ref<RoleCode | '' | undefined>
}

export function useUsers(options: UseUsersOptions = {}) {
  const queryClient = useQueryClient()
  const roleRef = options.roleName
  const roleKey = computed(() => roleRef?.value || '')

  const usersQuery = useQuery({
    queryKey: ['users', roleKey],
    queryFn: () => {
      const role = roleRef?.value || undefined
      return fetchUsers(role)
    },
    staleTime: STALE_TIME_MS.short,
  })

  const updateRole = useMutation({
    mutationFn: ({ userId, roleName, lineId }: { userId: string; roleName: RoleCode; lineId?: string | null }) =>
      updateUserRole(userId, roleName, lineId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  })

  const create = useMutation({
    mutationFn: (payload: UserCreatePayload) => createUser(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  })

  const updateLock = useMutation({
    mutationFn: ({ userId, locked }: { userId: string; locked: boolean }) =>
      updateUserLock(userId, locked),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  })

  const users = computed<UserResponse[]>(() => usersQuery.data.value ?? [])

  return {
    users,
    isPending: usersQuery.isPending,
    isError: usersQuery.isError,
    error: usersQuery.error,
    refetch: usersQuery.refetch,
    updateRole,
    updateLock,
    create,
  }
}
