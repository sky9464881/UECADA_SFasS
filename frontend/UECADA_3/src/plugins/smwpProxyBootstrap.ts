/** SMWP HTML 부트스트랩 스크립트 — KingPortal 로드 전 sessionStorage 세팅 */
export const SMWP_PROXY_PREFIX = '/swmp-proxy'

export const SMWP_BOOTSTRAP_SCRIPT = `<script>
(function () {
  var q = new URLSearchParams(window.location.search)
  var token = q.get('token')
  if (!token) return
  var page = q.get('page') || 'LDV'
  var user = q.get('u') || ''
  var rt = q.get('rt') || ''
  try {
    sessionStorage.setItem('WellinUserToken', token)
    sessionStorage.setItem('UserName', user)
    sessionStorage.setItem('user', user)
    if (rt) sessionStorage.setItem('refreshToken', rt)
    sessionStorage.setItem('hashPageName', page)
    sessionStorage.setItem('logintype', 'LOGINMODE')
    sessionStorage.setItem('proName', '')
    sessionStorage.setItem('projectName', '')
  } catch (e) {
    console.error('[uecada-swmp-bootstrap]', e)
  }
})()
</script>`

/** 절대경로(/extension, /static, /public)를 프록시 prefix 아래로 보정 */
export function rewriteSmwpAssetPaths(html: string, prefix = SMWP_PROXY_PREFIX): string {
  const p = prefix.replace(/\/$/, '')
  return html
    .replace(/(\s(?:src|href)=["'])\/(extension|static|public)\//gi, `$1${p}/$2/`)
    .replace(
      /<script[^>]*KingPortal_entrance\.js[^>]*><\/script>/i,
      (match) => `${match.replace(/src=["']\/extension\//i, `src="${p}/extension/`)}`,
    )
}

export function injectSmwpBootstrap(html: string): string {
  if (!html.includes('KingPortal_entrance.js')) return html
  if (html.includes('uecada-swmp-bootstrap')) return html
  const withPaths = rewriteSmwpAssetPaths(html)
  return withPaths.replace(
    /<script[^>]*KingPortal_entrance\.js[^>]*><\/script>/i,
    (match) => SMWP_BOOTSTRAP_SCRIPT + match,
  )
}
