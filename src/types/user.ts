export type RoleCode = 'ADMIN' | 'MANAGER' | 'OPERATOR' | string

export interface UserResponse {
  userId: string
  userName: string
  email: string | null
  roleName: RoleCode
  createdAt: string
}

export interface UserCreatePayload {
  userId: string
  loginId: string
  userName: string
  email?: string
  roleName: RoleCode
  password: string
}

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: '관리자',
  MANAGER: '매니저',
  OPERATOR: '작업자',
}

export const ROLE_OPTIONS: { code: RoleCode; label: string }[] = [
  { code: 'ADMIN', label: '관리자' },
  { code: 'MANAGER', label: '매니저' },
  { code: 'OPERATOR', label: '작업자' },
]

export function roleLabel(code: string | null | undefined): string {
  if (!code) return '-'
  return ROLE_LABEL[code] ?? code
}
