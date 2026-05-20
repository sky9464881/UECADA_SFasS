/**
 * WebSCADA(SMWP) 연결 유틸.
 * - 오버레이가 iframe 으로 SMWP 화면을 그대로 띄운다 (자동 로그인 시도하지 않음)
 */

export type WebScadaStageKey = 'cast' | 'mach' | 'wash' | 'assy' | 'insp'

const ALLOWED_HOSTS = new Set(['222.108.180.36', '192.168.0.100', 'localhost', '127.0.0.1'])

export function ldvPageIdForLine(lineId: string | null | undefined): string {
  const match = /(\d+)\s*$/.exec(lineId ?? '')
  if (!match) return 'LDV'
  const idx = Number.parseInt(match[1], 10)
  if (!Number.isFinite(idx) || idx < 1 || idx > 26) return 'LDV'
  return `LDV_${String.fromCharCode('A'.charCodeAt(0) + idx - 1)}`
}

const CATEGORY_ID_TO_ED_PAGE_ID: Record<string, string> = {
  casting: 'ED_CAST',
  machining: 'ED_CNC',
  washing: 'ED_WASH',
  assembly: 'ED_ASSY',
  inspection: 'ED_TEST',
}

export function edPageIdForCategory(categoryId: string | null | undefined): string {
  if (!categoryId) return 'ED'
  return CATEGORY_ID_TO_ED_PAGE_ID[categoryId] ?? 'ED'
}

function parseBaseUrl(raw: string): URL | null {
  const trimmed = raw.trim()
  if (!trimmed) return null
  try {
    const url = new URL(trimmed)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    if (!ALLOWED_HOSTS.has(url.hostname)) return null
    return url
  } catch {
    return null
  }
}

function hashFromDefaultUrl(defaultUrl: string): string {
  const hashIdx = defaultUrl.indexOf('#')
  if (hashIdx < 0) return ''
  return (defaultUrl.slice(hashIdx + 1).split('&')[0] ?? '').trim()
}

function proFromDefaultUrl(defaultUrl: string): string {
  try {
    const u = new URL(defaultUrl.split('#')[0] ?? defaultUrl)
    return (u.searchParams.get('Pro') ?? '').trim()
  } catch {
    return ''
  }
}

function readDefaultSwmpUrl(): string {
  return (import.meta.env.VITE_SWMP_DEFAULT_URL ?? '').trim()
}

export function getWebScadaUrl(): string | null {
  const raw = readDefaultSwmpUrl()
  if (!raw) return null
  const parsed = parseBaseUrl(raw.split('#')[0] ?? raw)
  if (!parsed) return null
  return raw
}

export function isWebScadaConfigured(): boolean {
  return getWebScadaUrl() !== null
}

function resolveSmwpPageId(pageOverride?: string): string {
  const raw = readDefaultSwmpUrl()
  return pageOverride || hashFromDefaultUrl(raw) || 'LDV'
}

function buildWebScadaUrlWithPage(pageId: string): string | null {
  const raw = readDefaultSwmpUrl()
  const base = parseBaseUrl(raw.split('#')[0] ?? raw)
  if (!base) return null
  const pro = proFromDefaultUrl(raw)
  const q = pro ? `?Pro=${encodeURIComponent(pro)}` : ''
  return `${base.origin}${base.pathname}${q}#${pageId}`
}

/** 오버레이가 iframe 으로 띄울 SMWP URL (예: http://host:11005/?Pro=...#LDV) */
export function buildSmwpOverlayUrl(pageId?: string): string | null {
  return buildWebScadaUrlWithPage(resolveSmwpPageId(pageId)) ?? getWebScadaUrl()
}
