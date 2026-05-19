import { api } from '@/api/client'

export interface LineGroupUser {
  userId: string
  loginId: string
  userName: string
  roleName: string
  lineId: string | null
}

export interface LineGroup {
  lineId: string
  lineName: string
  managers: LineGroupUser[]
  operators: LineGroupUser[]
}

export interface ChatRoom {
  chatRoomId: number
  lineId: string
  roomName: string
  roomType: 'LINE' | 'DIRECT' | string
  userAId: string | null
  userBId: string | null
  createdAt: string
  unreadCount: number
}

export interface ChatMessage {
  messageId: number
  chatRoomId: number
  senderUserId: string
  messageContent: string
  sentAt: string
}

export interface FactoryReport {
  generatedAt: string
  reportType: string
  title: string
  markdown: string
}

export type FactoryReportType = 'heat_safety' | 'annual_esg' | 'energy_emission'

export async function fetchLineGroups(): Promise<LineGroup[]> {
  const { data } = await api.get<LineGroup[]>('/api/community/line-groups')
  return data
}

export async function fetchChatRooms(currentUserId: string): Promise<ChatRoom[]> {
  const { data } = await api.get<ChatRoom[]>('/api/community/chat/rooms', {
    params: { currentUserId },
  })
  return data
}

export async function fetchChatMessages(roomId: number, currentUserId: string): Promise<ChatMessage[]> {
  const { data } = await api.get<ChatMessage[]>(`/api/community/chat/rooms/${roomId}/messages`, {
    params: { currentUserId },
  })
  return data
}

export async function sendChatMessage(roomId: number, senderUserId: string, messageContent: string): Promise<ChatMessage> {
  const { data } = await api.post<ChatMessage>(`/api/community/chat/rooms/${roomId}/messages`, {
    senderUserId,
    messageContent,
  })
  return data
}

export async function createDirectChatRoom(requesterUserId: string, targetUserId: string): Promise<ChatRoom> {
  const { data } = await api.post<ChatRoom>('/api/community/chat/rooms/direct', {
    requesterUserId,
    targetUserId,
  })
  return data
}

export async function fetchFactoryReport(type: FactoryReportType): Promise<FactoryReport> {
  const { data } = await api.get<FactoryReport>('/api/community/factory-report', {
    params: { type },
  })
  return data
}
