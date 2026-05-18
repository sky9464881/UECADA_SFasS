import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { createPost, fetchPosts } from '@/api/postApi'
import type { PostCreatePayload, PostResponse } from '@/types/post'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

export interface UsePostsOptions {
  category?: Ref<string | '' | undefined>
}

export function usePosts(options: UsePostsOptions = {}) {
  const queryClient = useQueryClient()
  const categoryRef = options.category
  const categoryKey = computed(() => categoryRef?.value || '')

  const postsQuery = useQuery({
    queryKey: ['posts', categoryKey],
    queryFn: () => fetchPosts(categoryRef?.value || undefined),
    staleTime: STALE_TIME_MS.short,
    refetchInterval: POLL_INTERVAL_MS.posts,
  })

  const create = useMutation({
    mutationFn: (payload: PostCreatePayload) => createPost(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['posts'] }),
  })

  const posts = computed<PostResponse[]>(() => postsQuery.data.value ?? [])

  return {
    posts,
    isPending: postsQuery.isPending,
    isError: postsQuery.isError,
    error: postsQuery.error,
    refetch: postsQuery.refetch,
    create,
  }
}
