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
  NOTICE: '공지',
  GENERAL: '일반',
  WORK_ORDER: '작업 지시',
  QUALITY: '품질',
  HANDOVER: '자료실',
  OPERATION: '운영',
}

export const CATEGORY_OPTIONS: { code: string; label: string }[] = [
  { code: 'NOTICE', label: '공지' },
  { code: 'GENERAL', label: '일반' },
  { code: 'WORK_ORDER', label: '작업 지시' },
  { code: 'QUALITY', label: '품질' },
  { code: 'HANDOVER', label: '자료실' },
  { code: 'OPERATION', label: '운영' },
]

export function categoryLabel(code: string | null | undefined): string {
  if (!code) return '일반'
  return CATEGORY_LABEL[code] ?? code
}
