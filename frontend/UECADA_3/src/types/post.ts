export interface PostResponse {
  postId: number
  authorUserId: string
  title: string
  content: string
  category: string | null
  targetLineId: string | null
  notice: boolean
  createdAt: string
}

export interface PostCreatePayload {
  authorUserId: string
  title: string
  content: string
  category?: string
  targetLineId?: string | null
  notice?: boolean
}

export const CATEGORY_LABEL: Record<string, string> = {
  NOTICE: '공지사항',
  QNA: 'Q&A',
  HANDOVER: '자료실',
}

export const CATEGORY_OPTIONS: { code: string; label: string }[] = [
  { code: 'NOTICE', label: '공지사항' },
  { code: 'QNA', label: 'Q&A' },
  { code: 'HANDOVER', label: '자료실' },
]

export function categoryLabel(code: string | null | undefined): string {
  if (!code) return '공지사항'
  return CATEGORY_LABEL[code] ?? code
}
