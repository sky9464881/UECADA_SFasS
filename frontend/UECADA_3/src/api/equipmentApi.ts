import { api } from '@/api/client'
import type { Equipment, EquipmentStatusItem } from '@/types/equipment'

export async function fetchEquipments(factoryId?: string): Promise<Equipment[]> {
  const { data } = await api.get<Equipment[]>('/api/equipments', {
    params: factoryId ? { factoryId } : undefined,
  })
  return data
}

export async function fetchEquipmentStatuses(equipIds: string[]): Promise<EquipmentStatusItem[]> {
  if (!equipIds.length) return []
  // Spring 의 @RequestParam List<String> 은 콤마 구분을 기본 지원.
  const { data } = await api.get<EquipmentStatusItem[]>('/api/equipment-status', {
    params: { equipIds: equipIds.join(',') },
  })
  return data
}
