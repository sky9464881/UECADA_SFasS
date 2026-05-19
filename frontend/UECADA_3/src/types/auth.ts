export type UserRole = 'admin' | 'manager' | 'operator'

export interface AuthUser {
  userId: string
  loginId: string
  userName: string
  email: string | null
  roleName: string
  lineId: string | null
}

export interface LoginPayload {
  loginId: string
  password: string
}

export interface SignupPayload {
  userId: string
  loginId: string
  lineId?: string | null
  userName: string
  email?: string
  roleName: string
  password: string
  securityQuestion: string
  securityAnswer: string
}
