import { CATEGORY_DEFINITIONS } from '@/composables/useEquipmentCatalog'

export interface EquipmentCodeCandidate {
  equipmentCode: string
  processType?: string | null
}

/** 알람·외부 코드(CAST-01 등)를 설비 목록의 전체 equipmentCode로 매칭 */
export function resolveEquipmentCode(
  raw: string | null | undefined,
  candidates: readonly EquipmentCodeCandidate[],
): string | null {
  const code = raw?.trim()
  if (!code || !candidates.length) return null

  const exact = candidates.find((c) => c.equipmentCode === code)
  if (exact) return exact.equipmentCode

  const upper = code.toUpperCase()
  const bySuffix = candidates.find((c) => {
    const id = c.equipmentCode.toUpperCase()
    return id.endsWith(`_${upper}`) || id.endsWith(upper) || id.includes(upper)
  })
  return bySuffix?.equipmentCode ?? null
}

export function categoryIdForProcessType(processType: string | null | undefined): string | null {
  if (!processType) return null
  return CATEGORY_DEFINITIONS.find((d) => d.processType === processType)?.id ?? null
}
