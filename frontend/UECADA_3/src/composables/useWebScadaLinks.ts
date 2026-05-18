/**
 * WebSCADA(SMWP) 팝업.
 * - uecada: UECADA 라인 대시보드 팝업
 * - external: SMWP 자동 로그인 (swmp-launch.html → ?Pro=...&token=...#화면ID)
 * - both: UECADA 팝업 + SMWP 팝업 동시
 */

export type WebScadaStageKey = 'cast' | 'mach' | 'wash' | 'assy' | 'insp'
export type SwmpOpenMode = 'uecada' | 'external' | 'both'

const POPUP_UECADA = 'uecada-webscada-line'
const POPUP_SMWP = 'uecada-webscada-smwp'
const POPUP_FEATURES =
  'popup=yes,width=1360,height=860,left=80,top=40,resizable=yes,scrollbars=yes,toolbar=no,menubar=no'

const ALLOWED_HOSTS = new Set(['222.108.180.36', 'localhost', '127.0.0.1'])

type SwmpLoginMessage = {
  type: 'uecada-swmp-login'
  base: string
  user: string
  pass: string
  pro: string
  page: string
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

/** URL 해시(#뒤) = SMWP 화면 ID (예: LDV) */
function hashFromDefaultUrl(defaultUrl: string): string {
  const hashIdx = defaultUrl.indexOf('#')
  if (hashIdx < 0) return ''
  const raw = defaultUrl.slice(hashIdx + 1).split('&')[0] ?? ''
  return raw.trim()
}

/** URL 쿼리 Pro= 프로젝트명 (예: yhh0518) */
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

function getOpenMode(): SwmpOpenMode {
  const m = (import.meta.env.VITE_SWMP_OPEN_MODE ?? 'uecada').trim().toLowerCase()
  if (m === 'external' || m === 'both') return m
  return 'uecada'
}

const SWMP_LOGIN_PAYLOAD_KEY = 'uecada-swmp-login-payload'

function scadaBaseOrigin(): string | null {
  const raw = readDefaultSwmpUrl()
  const base = parseBaseUrl(raw.split('#')[0] ?? raw)
  if (!base) return null
  return base.origin
}

function readSwmpCredentials(): { user: string; pass: string } {
  return {
    user: (import.meta.env.VITE_SWMP_USERNAME ?? '').trim(),
    pass: (import.meta.env.VITE_SWMP_PASSWORD ?? '').trim(),
  }
}

export function canAutoLoginWebScada(): boolean {
  const { user, pass } = readSwmpCredentials()
  return isWebScadaConfigured() && Boolean(user && pass)
}

/** 환경 변수 기준 WebSCADA URL (예: http://222.108.180.36:11005/?Pro=yhh0518#LDV) */
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

/** 공장 레이아웃 웹스카다 버튼 활성 여부 (uecada 모드는 URL 없어도 가능) */
export function canOpenWebScadaPopup(): boolean {
  return getOpenMode() === 'uecada' || isWebScadaConfigured()
}

function buildUecadaPopupUrl(lineId: string): string {
  const base = `${window.location.origin}${import.meta.env.BASE_URL}`
  return `${base}#/web-scada?line=${encodeURIComponent(lineId)}`
}

function buildWebScadaUrlWithPage(pageId: string): string | null {
  const raw = readDefaultSwmpUrl()
  const base = parseBaseUrl(raw.split('#')[0] ?? raw)
  if (!base) return null
  const pro = proFromDefaultUrl(raw)
  const q = pro ? `?Pro=${encodeURIComponent(pro)}` : ''
  return `${base.origin}${base.pathname}${q}#${pageId}`
}

function buildSwmpLoginPayload(pageOverride?: string): SwmpLoginMessage | null {
  const base = scadaBaseOrigin()
  const { user, pass } = readSwmpCredentials()
  const raw = readDefaultSwmpUrl()
  if (!base || !user || !pass) return null
  return {
    type: 'uecada-swmp-login',
    base,
    user,
    pass,
    pro: proFromDefaultUrl(raw),
    page: pageOverride || hashFromDefaultUrl(raw) || 'LDV',
  }
}

function postSwmpLogin(win: Window, pageOverride?: string): void {
  const payload = buildSwmpLoginPayload(pageOverride)
  if (!payload) return
  win.postMessage(payload, window.location.origin)
}

function stashSwmpLoginPayload(pageOverride?: string): void {
  const payload = buildSwmpLoginPayload(pageOverride)
  if (!payload) return
  sessionStorage.setItem(SWMP_LOGIN_PAYLOAD_KEY, JSON.stringify(payload))
}

export function openUecadaWebScadaPopup(lineId = 'LINE-A'): boolean {
  const win = window.open(buildUecadaPopupUrl(lineId), POPUP_UECADA, POPUP_FEATURES)
  if (!win) return false
  win.focus()
  return true
}

export function openWebScadaWithAutoLogin(pageId?: string): boolean {
  if (!canAutoLoginWebScada()) return false

  stashSwmpLoginPayload(pageId)

  const launchUrl = `${window.location.origin}${import.meta.env.BASE_URL}swmp-launch.html`
  const win = window.open(launchUrl, POPUP_SMWP, POPUP_FEATURES)
  if (!win) return false

  const onMessage = (event: MessageEvent) => {
    if (event.origin !== window.location.origin) return
    if (event.data?.type === 'uecada-swmp-launch-ready') {
      postSwmpLogin(win, pageId)
    }
  }
  window.addEventListener('message', onMessage)
  window.setTimeout(() => postSwmpLogin(win, pageId), 400)
  window.setTimeout(() => window.removeEventListener('message', onMessage), 10_000)

  win.focus()
  return true
}

/** 외부 SMWP 팝업 — pageId 로 #LDV / #ED 등 화면 지정 가능 */
export function openExternalWebScada(pageId?: string): boolean {
  if (canAutoLoginWebScada()) return openWebScadaWithAutoLogin(pageId)

  const url = pageId ? buildWebScadaUrlWithPage(pageId) : getWebScadaUrl()
  if (!url) return false

  const win = window.open(url, POPUP_SMWP, POPUP_FEATURES)
  if (!win) return false
  win.focus()
  return true
}

/** 설비 상세보기 → SMWP 설비 화면 (#ED) */
export function openEquipmentWebScada(): boolean {
  return openExternalWebScada('ED')
}

/** 공장 레이아웃 「웹스카다」 버튼 */
export function openWebScadaPopup(lineId = 'LINE-A'): boolean {
  const mode = getOpenMode()

  if (mode === 'external') {
    return openExternalWebScada()
  }

  if (mode === 'both') {
    const u = openUecadaWebScadaPopup(lineId)
    const e = openExternalWebScada()
    return u || e
  }

  return openUecadaWebScadaPopup(lineId)
}
