import { api } from '@/api/client'
import type { PostCreatePayload, PostResponse } from '@/types/post'

export async function fetchPosts(category?: string): Promise<PostResponse[]> {
  const { data } = await api.get<PostResponse[]>('/api/posts', {
    params: category ? { category } : undefined,
  })
  return data
}

export async function createPost(payload: PostCreatePayload): Promise<PostResponse> {
  const { data } = await api.post<PostResponse>('/api/posts', payload)
  return data
}
