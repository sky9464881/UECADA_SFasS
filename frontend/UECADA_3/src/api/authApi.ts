import { api } from '@/api/client'
import type { AuthUser, LoginPayload, SignupPayload } from '@/types/auth'

export interface FindLoginIdPayload {
  userName: string
  email: string
  securityAnswer: string
}

export interface FindLoginIdResponse {
  loginId: string
}

export interface SecurityQuestionResponse {
  loginId: string
  securityQuestion: string | null
}

export interface ResetPasswordPayload {
  loginId: string
  securityAnswer: string
  newPassword: string
}

export async function login(payload: LoginPayload): Promise<AuthUser> {
  const { data } = await api.post<AuthUser>('/api/auth/login', payload)
  return data
}

export async function signup(payload: SignupPayload): Promise<AuthUser> {
  const { data } = await api.post<AuthUser>('/api/auth/signup', payload)
  return data
}

export async function findLoginId(payload: FindLoginIdPayload): Promise<FindLoginIdResponse> {
  const { data } = await api.post<FindLoginIdResponse>('/api/auth/find-id', payload)
  return data
}

export async function fetchSecurityQuestion(loginId: string): Promise<SecurityQuestionResponse> {
  const { data } = await api.get<SecurityQuestionResponse>('/api/auth/security-question', {
    params: { loginId },
  })
  return data
}

export async function resetPassword(payload: ResetPasswordPayload): Promise<void> {
  await api.post('/api/auth/reset-password', payload)
}
