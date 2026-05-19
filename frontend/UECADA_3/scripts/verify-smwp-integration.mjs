import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), 'utf8')
}

function assertIncludes(content, expected, label) {
  if (!content.includes(expected)) {
    throw new Error(`${label}: expected to include ${JSON.stringify(expected)}`)
  }
}

function assertMatch(content, pattern, label) {
  if (!pattern.test(content)) {
    throw new Error(`${label}: expected to match ${pattern}`)
  }
}

const factoryPage = read('src/components/FactoryLayoutPage.vue')
const equipmentPage = read('src/components/EquipmentDetailPage.vue')
const webScadaLinks = read('src/composables/useWebScadaLinks.ts')
const overlay = read('src/components/WebScadaOverlay.vue')

assertIncludes(factoryPage, "defineAsyncComponent(() => import('@/components/WebScadaOverlay.vue'))", 'Factory page overlay import')
assertIncludes(factoryPage, 'ldvPageIdForLine', 'Factory page line page-id mapper')
assertIncludes(factoryPage, 'isWebScadaConfigured', 'Factory page configured guard')
assertIncludes(factoryPage, 'function openWebScada', 'Factory page open handler')
assertIncludes(factoryPage, '<WebScadaOverlay', 'Factory page overlay render')
assertIncludes(factoryPage, ':page-id="webScadaOverlayPageId"', 'Factory page overlay page id binding')

assertIncludes(equipmentPage, "defineAsyncComponent(() => import('@/components/WebScadaOverlay.vue'))", 'Equipment page overlay import')
assertIncludes(equipmentPage, 'edPageIdForCategory', 'Equipment page category page-id mapper')
assertIncludes(equipmentPage, 'isWebScadaConfigured', 'Equipment page configured guard')
assertIncludes(equipmentPage, 'function openWebScada', 'Equipment page open handler')
assertIncludes(equipmentPage, '<WebScadaOverlay', 'Equipment page overlay render')
assertIncludes(equipmentPage, ':page-id="webScadaOverlayPageId"', 'Equipment page overlay page id binding')

assertMatch(webScadaLinks, /export function ldvPageIdForLine[\s\S]+LDV_\$\{String\.fromCharCode/, 'Line-to-LDV mapper')
assertIncludes(webScadaLinks, "casting: 'ED_CAST'", 'Category-to-ED mapper')
assertIncludes(webScadaLinks, 'export function edPageIdForCategory', 'Category-to-ED export')
assertIncludes(webScadaLinks, 'VITE_SWMP_DEFAULT_URL', 'SMWP env source')
assertIncludes(webScadaLinks, 'buildSmwpOverlayUrl', 'Overlay URL builder')

assertIncludes(overlay, 'buildSmwpOverlayUrl(props.pageId)', 'Overlay consumes page id')
assertIncludes(overlay, '<iframe', 'Overlay iframe render')

console.log('[OK] SMWP integration contract verified')
