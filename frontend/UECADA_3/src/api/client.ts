import axios from 'axios'
import { HTTP_TIMEOUT_MS } from '@/constants/polling'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

export const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: HTTP_TIMEOUT_MS,
})
