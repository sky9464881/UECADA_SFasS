<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import {
  Bell,
  ChevronDown,
  Download,
  Eye,
  FileText,
  LogOut,
  Megaphone,
  MessageSquare,
  Pencil,
  Search,
  Send,
  Sparkles,
  UserRound,
  Users,
} from 'lucide-vue-next'
import {
  createDirectChatRoom,
  fetchChatMessages,
  fetchChatRooms,
  fetchFactoryReport,
  fetchLineGroups,
  sendChatMessage,
  type FactoryReport,
  type FactoryReportType,
  type LineGroup,
  type LineGroupUser,
} from '@/api/communityApi'
import { createPost, fetchPosts } from '@/api/postApi'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useAuthStore } from '@/stores/auth'
import { CATEGORY_OPTIONS, categoryLabel } from '@/types/post'

const { navItems } = useAppNav()
const logout = useLogout()
const auth = useAuthStore()
const queryClient = useQueryClient()

const currentUser = computed(() => auth.user ?? {
  userId: 'U001',
  loginId: 'admin',
  userName: '관리자',
  email: 'admin@uecada.com',
  roleName: 'ADMIN',
  lineId: null,
})

const boardTabs = [
  { code: 'NOTICE', label: '공지사항' },
  { code: 'GENERAL', label: '일반 게시판' },
  { code: 'WORK_ORDER', label: '작업 지시' },
  { code: 'HANDOVER', label: '자료실' },
] as const

const reportTabs: { type: FactoryReportType; label: string }[] = [
  { type: 'heat_safety', label: '폭염 안전관리 보고서' },
  { type: 'annual_esg', label: '연간 ESG 운영 보고서' },
  { type: 'energy_emission', label: '전력 사용 및 탄소 배출 보고서' },
]

const selectedBoardCategory = ref<(typeof boardTabs)[number]['code']>('NOTICE')
const selectedGroupLineId = ref<string>('ALL')
const selectedRoomId = ref<number | null>(null)
const selectedReportType = ref<FactoryReportType>('heat_safety')
const selectedGroupModal = ref<LineGroup | null>(null)
const chatMode = ref<'LINE' | 'DIRECT'>('LINE')
const chatDraft = ref('')
const searchText = ref('')
const directSearch = ref('')
const reportByType = ref<Partial<Record<FactoryReportType, FactoryReport>>>({})
const showPostComposer = ref(false)
const boardPanelRef = ref<HTMLElement | null>(null)

const postForm = reactive({
  title: '',
  content: '',
  category: 'NOTICE',
  targetLineId: '',
  notice: true,
})

const lineGroupsQuery = useQuery({
  queryKey: ['community', 'line-groups'],
  queryFn: fetchLineGroups,
  staleTime: 10_000,
  refetchInterval: 10_000,
  refetchIntervalInBackground: true,
})

const postsQuery = useQuery({
  queryKey: computed(() => ['community', 'posts', selectedBoardCategory.value]),
  queryFn: () => fetchPosts(selectedBoardCategory.value),
  staleTime: 2_000,
  refetchInterval: 5_000,
  refetchIntervalInBackground: true,
})

const roomsQuery = useQuery({
  queryKey: computed(() => ['community', 'chat-rooms', currentUser.value.userId]),
  queryFn: () => fetchChatRooms(currentUser.value.userId),
  staleTime: 2_000,
  refetchInterval: 5_000,
  refetchIntervalInBackground: true,
})

const messagesQuery = useQuery({
  queryKey: computed(() => ['community', 'chat-messages', selectedRoomId.value, currentUser.value.userId]),
  queryFn: () => selectedRoomId.value
    ? fetchChatMessages(selectedRoomId.value, currentUser.value.userId)
    : Promise.resolve([]),
  enabled: computed(() => selectedRoomId.value != null),
  staleTime: 0,
  refetchInterval: 2_000,
  refetchIntervalInBackground: true,
})

watch(selectedBoardCategory, (category) => {
  postForm.category = category
  postForm.notice = category === 'NOTICE'
})

const groups = computed(() => lineGroupsQuery.data.value ?? [])
const allUsers = computed(() =>
  groups.value.flatMap((group) => groupMembers(group)),
)
const posts = computed(() => postsQuery.data.value ?? [])
const pinnedPosts = computed(() => posts.value.filter((post) => post.notice).slice(0, 3))
const tablePosts = computed(() => posts.value.filter((post) => !post.notice).slice(0, 8))
const rooms = computed(() => roomsQuery.data.value ?? [])
const filteredRooms = computed(() =>
  rooms.value.filter((room) => {
    const roomType = String(room.roomType).toUpperCase()
    return chatMode.value === 'DIRECT' ? roomType === 'DIRECT' : roomType !== 'DIRECT'
  }),
)
const selectedRoom = computed(() => rooms.value.find((room) => room.chatRoomId === selectedRoomId.value) ?? null)
const messages = computed(() => messagesQuery.data.value ?? [])
const selectedReport = computed(() => reportByType.value[selectedReportType.value] ?? null)

watch(
  () => [roomsQuery.data.value, chatMode.value] as const,
  () => {
    const roomsForMode = filteredRooms.value
    if (!roomsForMode.length) {
      selectedRoomId.value = null
      return
    }
    if (!selectedRoomId.value || !roomsForMode.some((room) => room.chatRoomId === selectedRoomId.value)) {
      selectedRoomId.value = roomsForMode[0].chatRoomId
    }
  },
  { immediate: true },
)

const filteredGroups = computed(() =>
  selectedGroupLineId.value === 'ALL'
    ? groups.value
    : groups.value.filter((group) => group.lineId === selectedGroupLineId.value),
)

const directCandidates = computed(() => {
  const keyword = directSearch.value.trim().toLowerCase()
  return allUsers.value
    .filter((user) => canDirectChat(user))
    .filter((user) => {
      if (!keyword) return true
      return `${user.userName} ${user.loginId} ${user.lineId ?? ''}`.toLowerCase().includes(keyword)
    })
})

const selectedReportLabel = computed(() =>
  reportTabs.find((tab) => tab.type === selectedReportType.value)?.label ?? '자동 문서'
)

const isCreatingPost = computed(() => createPostMutation.isPending.value)
const isSendingMessage = computed(() => sendMessageMutation.isPending.value)
const isCreatingDirectRoom = computed(() => directRoomMutation.isPending.value)
const isGeneratingReport = computed(() => reportMutation.isPending.value)

const createPostMutation = useMutation({
  mutationFn: () => createPost({
    authorUserId: currentUser.value.userId,
    title: postForm.title,
    content: postForm.content,
    category: postForm.category,
    targetLineId: postForm.targetLineId || null,
    notice: postForm.notice,
  }),
  onSuccess: () => {
    postForm.title = ''
    postForm.content = ''
    showPostComposer.value = false
    queryClient.invalidateQueries({ queryKey: ['community', 'posts'] })
  },
})

const sendMessageMutation = useMutation({
  mutationFn: () => selectedRoomId.value
    ? sendChatMessage(selectedRoomId.value, currentUser.value.userId, chatDraft.value)
    : Promise.reject(new Error('No room selected')),
  onSuccess: () => {
    chatDraft.value = ''
    queryClient.invalidateQueries({ queryKey: ['community', 'chat-messages'] })
  },
})

const directRoomMutation = useMutation({
  mutationFn: (targetUserId: string) => createDirectChatRoom(currentUser.value.userId, targetUserId),
  onSuccess: (room) => {
    chatMode.value = 'DIRECT'
    selectedRoomId.value = room.chatRoomId
    queryClient.invalidateQueries({ queryKey: ['community', 'chat-rooms'] })
  },
})

const reportMutation = useMutation({
  mutationFn: (type: FactoryReportType) => fetchFactoryReport(type),
  onSuccess: (report) => {
    reportByType.value = { ...reportByType.value, [report.reportType as FactoryReportType]: report }
  },
})

const reportPreviewSrcdoc = computed(() => {
  const markdown = selectedReport.value?.markdown
  const html = markdown
    ? markdownToHtml(markdown)
    : `<div class="empty-preview">
        <h1>${escapeHtml(selectedReportLabel.value)}</h1>
        <p>자동 문서화 생성 버튼을 누르면 현재 공장 버퍼 데이터가 반영된 Markdown 보고서를 표 형식으로 렌더링합니다.</p>
      </div>`

  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <style>
    body { margin: 0; padding: 24px; color: #0f172a; font: 14px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #fff; }
    h1 { margin: 0 0 18px; font-size: 24px; line-height: 1.25; }
    h2 { margin: 24px 0 12px; font-size: 18px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }
    h3 { margin: 20px 0 10px; font-size: 15px; }
    p { margin: 8px 0; }
    ul { margin: 8px 0 14px; padding-left: 20px; }
    li { margin: 4px 0; }
    table { width: 100%; border-collapse: collapse; margin: 12px 0 18px; table-layout: auto; }
    th, td { border: 1px solid #dbe5f0; padding: 8px 10px; text-align: left; vertical-align: top; word-break: keep-all; }
    th { background: #f3f7fc; color: #0f1f38; font-weight: 800; }
    tr:nth-child(even) td { background: #fbfdff; }
    code { border-radius: 4px; background: #eef2f7; padding: 1px 5px; font-family: Consolas, monospace; }
    .empty-preview { min-height: 220px; display: grid; place-content: center; text-align: center; color: #475569; }
  </style>
</head>
<body>${html}</body>
</html>`
})

function submitPost() {
  if (!postForm.title.trim() || !postForm.content.trim()) return
  createPostMutation.mutate()
}

function submitMessage() {
  if (!chatDraft.value.trim() || !selectedRoomId.value) return
  sendMessageMutation.mutate()
}

function generateReport() {
  reportMutation.mutate(selectedReportType.value)
}

function downloadReport() {
  const report = selectedReport.value
  if (!report) return
  const blob = new Blob([report.markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `uecada-${report.reportType}-${new Date().toISOString().slice(0, 10)}.md`
  link.click()
  URL.revokeObjectURL(url)
}

function groupMembers(group: LineGroup): LineGroupUser[] {
  return [...group.managers, ...group.operators]
}

function groupWorkerCount(group: LineGroup): number {
  return group.operators.length
}

function groupTotalCount(group: LineGroup): number {
  return group.managers.length + group.operators.length
}

function openNoticeComposer(group: LineGroup) {
  postForm.category = 'NOTICE'
  postForm.targetLineId = group.lineId
  postForm.notice = true
  postForm.title = `${group.lineName} 공지`
  postForm.content = ''
  selectedBoardCategory.value = 'NOTICE'
  showPostComposer.value = true
  requestAnimationFrame(() => boardPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function openGroupModal(group: LineGroup) {
  selectedGroupModal.value = group
}

function directChat(user: LineGroupUser) {
  if (!canDirectChat(user)) return
  directRoomMutation.mutate(user.userId)
}

function canDirectChat(user: LineGroupUser): boolean {
  if (user.userId === currentUser.value.userId) return false
  if (currentUser.value.roleName?.toUpperCase() === 'ADMIN') return true
  return !!currentUser.value.lineId && currentUser.value.lineId === user.lineId
}

function userName(userId: string): string {
  return allUsers.value.find((user) => user.userId === userId)?.userName ?? userId
}

function roomSubtitle(roomType: string): string {
  return String(roomType).toUpperCase() === 'DIRECT' ? '1:1 채팅' : '라인 채팅'
}

function roleLabel(roleName: string | null | undefined): string {
  const role = String(roleName ?? '').toUpperCase()
  if (role === 'ADMIN') return '관리자'
  if (role === 'MANAGER') return '라인 관리자'
  return '작업자'
}

function formatDate(iso: string | null | undefined) {
  return iso ? iso.replace('T', ' ').slice(0, 10) : '-'
}

function formatTime(iso: string | null | undefined) {
  return iso ? iso.replace('T', ' ').slice(11, 16) : '-'
}

function initials(name: string): string {
  const cleaned = name.trim()
  return cleaned.length <= 2 ? cleaned || '사용자' : cleaned.slice(-2)
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInline(value: string): string {
  return escapeHtml(value).replace(/`([^`]+)`/g, '<code>$1</code>')
}

function isTableRow(line: string): boolean {
  const trimmed = line.trim()
  return trimmed.startsWith('|') && trimmed.endsWith('|')
}

function isTableSeparator(line: string): boolean {
  return /^\|\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|$/.test(line.trim())
}

function splitTableRow(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim())
}

function markdownToHtml(markdown: string): string {
  const lines = markdown.split(/\r?\n/)
  const html: string[] = []
  let i = 0
  let listOpen = false

  const closeList = () => {
    if (listOpen) {
      html.push('</ul>')
      listOpen = false
    }
  }

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    if (!trimmed) {
      closeList()
      i += 1
      continue
    }

    if (isTableRow(trimmed) && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      closeList()
      const headers = splitTableRow(trimmed)
      i += 2
      const rows: string[][] = []
      while (i < lines.length && isTableRow(lines[i])) {
        rows.push(splitTableRow(lines[i]))
        i += 1
      }
      html.push('<table><thead><tr>')
      html.push(headers.map((cell) => `<th>${renderInline(cell)}</th>`).join(''))
      html.push('</tr></thead><tbody>')
      html.push(rows.map((row) => `<tr>${row.map((cell) => `<td>${renderInline(cell)}</td>`).join('')}</tr>`).join(''))
      html.push('</tbody></table>')
      continue
    }

    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
    if (heading) {
      closeList()
      const level = heading[1].length
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      i += 1
      continue
    }

    if (trimmed.startsWith('- ')) {
      if (!listOpen) {
        html.push('<ul>')
        listOpen = true
      }
      html.push(`<li>${renderInline(trimmed.slice(2))}</li>`)
      i += 1
      continue
    }

    closeList()
    html.push(`<p>${renderInline(trimmed)}</p>`)
    i += 1
  }

  closeList()
  return html.join('')
}
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar" aria-label="주요 메뉴">
      <RouterLink class="dashboard-brand" :to="{ name: 'dashboard' }">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스마트 공장</small>
        </span>
      </RouterLink>

      <nav class="dashboard-nav">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.to">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <span>{{ roleLabel(currentUser.roleName) }}</span>
        <strong>{{ currentUser.userName }}</strong>
        <p>라인 그룹, 게시판, 채팅, 자동 문서화를 관리합니다.</p>
      </div>
    </aside>

    <section class="dashboard-main community-main">
      <header class="community-topbar">
        <div>
          <p class="dashboard-kicker">Community</p>
          <h1>커뮤니티</h1>
        </div>
        <div class="community-topbar-actions">
          <label class="community-search">
            <input v-model="searchText" type="search" placeholder="검색어를 입력하세요." />
            <Search :size="18" />
          </label>
          <button type="button" class="community-bell" aria-label="공지 알림">
            <Bell :size="20" />
            <b>{{ pinnedPosts.length }}</b>
          </button>
          <span class="community-user-chip">
            <UserRound :size="20" />
            <strong>{{ currentUser.userName }}</strong>
            <ChevronDown :size="16" />
          </span>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <section ref="boardPanelRef" class="community-board-panel">
        <div class="community-board-tabs" role="tablist" aria-label="게시판 유형">
          <button
            v-for="tab in boardTabs"
            :key="tab.code"
            type="button"
            :class="{ active: selectedBoardCategory === tab.code }"
            @click="selectedBoardCategory = tab.code"
          >
            {{ tab.label }}
          </button>
          <button class="community-write-button" type="button" @click="showPostComposer = !showPostComposer">
            <Pencil :size="16" />
            <span>글쓰기</span>
          </button>
        </div>

        <form v-if="showPostComposer" class="community-compose" @submit.prevent="submitPost">
          <div class="community-compose-row">
            <select v-model="postForm.category">
              <option v-for="opt in CATEGORY_OPTIONS" :key="opt.code" :value="opt.code">{{ opt.label }}</option>
            </select>
            <select v-model="postForm.targetLineId">
              <option value="">전체 라인</option>
              <option v-for="group in groups" :key="group.lineId" :value="group.lineId">{{ group.lineName }}</option>
            </select>
            <label class="community-checkbox">
              <input v-model="postForm.notice" type="checkbox" />
              상단 공지
            </label>
          </div>
          <input v-model="postForm.title" type="text" placeholder="제목" />
          <textarea v-model="postForm.content" rows="3" placeholder="내용을 입력하세요."></textarea>
          <button class="primary-action" type="submit" :disabled="isCreatingPost">
            <Megaphone :size="16" />
            <span>{{ isCreatingPost ? '등록 중' : '등록' }}</span>
          </button>
        </form>

        <div class="community-notice-list">
          <article v-for="post in pinnedPosts" :key="`pin-${post.postId}`">
            <Megaphone :size="16" />
            <strong>{{ categoryLabel(post.category) }}</strong>
            <span>{{ post.title }}</span>
            <b>{{ post.targetLineId || '전체' }}</b>
            <small>{{ formatDate(post.createdAt) }}</small>
          </article>
          <article v-if="!pinnedPosts.length" class="community-empty-row">
            <span>등록된 공지사항이 없습니다.</span>
          </article>
        </div>

        <table class="community-board-table">
          <thead>
            <tr>
              <th>번호</th>
              <th>제목</th>
              <th>카테고리</th>
              <th>라인</th>
              <th>작성일</th>
              <th>조회수</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="post in tablePosts" :key="post.postId">
              <td>{{ post.postId }}</td>
              <td>{{ post.title }}</td>
              <td><span class="community-category-badge">{{ categoryLabel(post.category) }}</span></td>
              <td>{{ post.targetLineId || '전체' }}</td>
              <td>{{ formatDate(post.createdAt) }}</td>
              <td>{{ Math.max(1, post.postId % 137) }}</td>
            </tr>
            <tr v-if="!tablePosts.length">
              <td colspan="6">게시글이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="community-lower-grid">
        <article class="community-card community-line-card">
          <header class="community-card-head">
            <div>
              <p class="panel-kicker">1) Line Group</p>
              <h2>라인 그룹</h2>
            </div>
            <Users :size="22" />
          </header>

          <div class="community-line-tabs">
            <button type="button" :class="{ active: selectedGroupLineId === 'ALL' }" @click="selectedGroupLineId = 'ALL'">전체</button>
            <button
              v-for="group in groups"
              :key="group.lineId"
              type="button"
              :class="{ active: selectedGroupLineId === group.lineId }"
              @click="selectedGroupLineId = group.lineId"
            >
              {{ group.lineName }}
            </button>
          </div>

          <div class="community-group-stack">
            <article v-for="group in filteredGroups" :key="group.lineId" class="community-group-card">
              <div class="community-group-main">
                <div>
                  <strong>{{ group.lineName }}</strong>
                  <span>작업자 {{ groupWorkerCount(group) }}명 · 관리자 {{ group.managers.length }}명</span>
                </div>
                <b>{{ groupTotalCount(group) }}명</b>
              </div>
              <div class="community-avatar-row">
                <button
                  v-for="user in groupMembers(group).slice(0, 8)"
                  :key="user.userId"
                  type="button"
                  :title="`${user.userName} 1:1 채팅`"
                  :disabled="!canDirectChat(user)"
                  @click="directChat(user)"
                >
                  {{ initials(user.userName) }}
                </button>
                <span v-if="groupTotalCount(group) > 8">+{{ groupTotalCount(group) - 8 }}</span>
              </div>
              <div class="community-group-actions">
                <button type="button" @click="openNoticeComposer(group)">
                  <Megaphone :size="14" />
                  공지 보내기
                </button>
                <button type="button" @click="openGroupModal(group)">
                  <Eye :size="14" />
                  그룹 보기
                </button>
              </div>
            </article>
          </div>
        </article>

        <article class="community-card community-chat-card">
          <header class="community-card-head">
            <div>
              <p class="panel-kicker">2) Chat</p>
              <h2>채팅</h2>
            </div>
            <MessageSquare :size="22" />
          </header>

          <div class="community-chat-tabs" role="tablist" aria-label="채팅 유형">
            <button
              type="button"
              :class="{ active: chatMode === 'LINE' }"
              @click="chatMode = 'LINE'"
            >
              라인 채팅
            </button>
            <button
              type="button"
              :class="{ active: chatMode === 'DIRECT' }"
              @click="chatMode = 'DIRECT'"
            >
              1:1 채팅
            </button>
          </div>

          <div class="community-chat-layout">
            <aside class="community-room-list">
              <div class="community-room-scroll">
                <!-- 통합된 스크롤 영역: 검색(1:1 모드) + 1:1 후보 목록(스크롤) + 채팅방 목록 -->
                <template v-if="chatMode === 'DIRECT'">
                  <div class="community-direct-start">
                    <strong>1:1 대화 시작</strong>
                    <input v-model="directSearch" type="search" placeholder="이름/라인 검색" />
                  </div>

                  <div class="community-direct-list">
                    <button
                      v-for="user in directCandidates"
                      :key="user.userId"
                      type="button"
                      :disabled="isCreatingDirectRoom"
                      @click="directChat(user)"
                    >
                      <span>{{ initials(user.userName) }}</span>
                      <b>{{ user.userName }}</b>
                      <small>{{ user.lineId || '전체' }}</small>
                    </button>
                    <p v-if="!directCandidates.length" class="community-room-empty">대화 가능한 사용자가 없습니다.</p>
                  </div>
                </template>

                <div class="community-room-rooms">
                  <button
                    v-for="room in filteredRooms"
                    :key="room.chatRoomId"
                    type="button"
                    :class="{ active: selectedRoomId === room.chatRoomId }"
                    @click="selectedRoomId = room.chatRoomId"
                  >
                    <strong>{{ room.roomName }}</strong>
                    <span>{{ roomSubtitle(room.roomType) }}</span>
                  </button>
                  <p v-if="!filteredRooms.length" class="community-room-empty">
                    {{ chatMode === 'DIRECT' ? '1:1 채팅방이 없습니다.' : '라인 채팅방이 없습니다.' }}
                  </p>
                </div>
              </div>
            </aside>

            <section class="community-chat-panel">
              <header>
                <div>
                  <strong>{{ selectedRoom?.roomName || '채팅방 선택' }}</strong>
                  <span>{{ selectedRoom ? roomSubtitle(selectedRoom.roomType) : '라인 또는 1:1 채팅' }}</span>
                </div>
                <b>{{ messages.length }}개 메시지</b>
              </header>
              <div class="community-message-list">
                <article
                  v-for="message in messages"
                  :key="message.messageId"
                  :class="{ mine: message.senderUserId === currentUser.userId }"
                >
                  <small>{{ userName(message.senderUserId) }} · {{ formatTime(message.sentAt) }}</small>
                  <p>{{ message.messageContent }}</p>
                </article>
                <p v-if="!messages.length" class="community-empty-row">아직 메시지가 없습니다.</p>
              </div>
              <form class="community-chat-input" @submit.prevent="submitMessage">
                <input
                  v-model="chatDraft"
                  type="text"
                  :placeholder="selectedRoom ? '메시지를 입력하세요.' : '채팅방을 선택하세요.'"
                />
                <button type="submit" :disabled="isSendingMessage || !selectedRoomId">
                  <Send :size="18" />
                </button>
              </form>
            </section>
          </div>
        </article>

        <article class="community-card community-doc-card">
          <header class="community-card-head">
            <div>
              <p class="panel-kicker">3) Auto Documentation</p>
              <h2>자동 문서화</h2>
            </div>
            <button class="community-doc-generate" type="button" :disabled="isGeneratingReport" @click="generateReport">
              <Sparkles :size="16" />
              <span>{{ isGeneratingReport ? '생성 중' : '자동 문서화 생성' }}</span>
            </button>
          </header>

          <div class="community-report-tabs" role="tablist" aria-label="자동 문서 종류">
            <button
              v-for="tab in reportTabs"
              :key="tab.type"
              type="button"
              :class="{ active: selectedReportType === tab.type }"
              @click="selectedReportType = tab.type"
            >
              {{ tab.label }}
            </button>
          </div>

          <div class="community-report-box">
            <aside>
              <FileText :size="24" />
              <strong>{{ selectedReportLabel }}</strong>
              <span>{{ selectedReport ? formatDate(selectedReport.generatedAt) : '생성 대기' }}</span>
              <button class="ghost-button" type="button" :disabled="!selectedReport" @click="downloadReport">
                <Download :size="16" />
                <span>저장</span>
              </button>
            </aside>
            <iframe
              class="community-report-frame"
              title="자동 문서화 Markdown 미리보기"
              :srcdoc="reportPreviewSrcdoc"
            />
          </div>
        </article>
      </section>

      <div v-if="selectedGroupModal" class="community-modal-backdrop" @click.self="selectedGroupModal = null">
        <section class="community-group-modal" role="dialog" aria-modal="true" :aria-label="`${selectedGroupModal.lineName} 그룹 보기`">
          <header>
            <div>
              <p class="panel-kicker">Line Group</p>
              <h2>{{ selectedGroupModal.lineName }} 그룹</h2>
            </div>
            <button type="button" @click="selectedGroupModal = null">닫기</button>
          </header>
          <div class="community-group-modal-grid">
            <article>
              <h3>관리자 {{ selectedGroupModal.managers.length }}명</h3>
              <button
                v-for="user in selectedGroupModal.managers"
                :key="user.userId"
                type="button"
                :disabled="!canDirectChat(user)"
                @click="directChat(user)"
              >
                <span>{{ initials(user.userName) }}</span>
                <b>{{ user.userName }}</b>
                <small>{{ user.loginId }}</small>
              </button>
            </article>
            <article>
              <h3>작업자 {{ selectedGroupModal.operators.length }}명</h3>
              <button
                v-for="user in selectedGroupModal.operators"
                :key="user.userId"
                type="button"
                :disabled="!canDirectChat(user)"
                @click="directChat(user)"
              >
                <span>{{ initials(user.userName) }}</span>
                <b>{{ user.userName }}</b>
                <small>{{ user.loginId }}</small>
              </button>
            </article>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
.community-main {
  gap: 14px;
  overflow-x: hidden;
}

.community-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.community-topbar h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 900;
  line-height: 1.2;
}

.community-topbar-actions,
.community-search,
.community-user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.community-search {
  width: min(420px, 42vw);
  border: 1px solid #d6e1ee;
  border-radius: 8px;
  padding: 0 12px;
  background: #fff;
}

.community-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  padding: 11px 0;
  font: inherit;
}

.community-bell {
  position: relative;
  width: 40px;
  height: 40px;
  border: 0;
  background: #fff;
  border-radius: 8px;
  color: #0f1f38;
  display: grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.community-bell b {
  position: absolute;
  right: -3px;
  top: -4px;
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
}

.community-user-chip {
  border-radius: 999px;
  background: #fff;
  padding: 7px 11px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.community-board-panel,
.community-card {
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
}

.community-board-panel {
  padding: 0 20px 18px;
}

.community-board-tabs {
  display: flex;
  align-items: center;
  gap: 36px;
  min-height: 64px;
  border-bottom: 1px solid #e2e8f0;
}

.community-board-tabs button {
  border: 0;
  background: transparent;
  color: #475569;
  font-weight: 900;
  cursor: pointer;
  white-space: nowrap;
}

.community-board-tabs button.active {
  color: #1d4ed8;
  box-shadow: inset 0 -3px 0 #2563eb;
  align-self: stretch;
}

.community-board-tabs .community-write-button {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 8px;
  background: #1d5ee9;
  color: #fff;
}

.community-compose {
  display: grid;
  gap: 8px;
  margin: 14px 0;
  padding: 12px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #f8fbff;
}

.community-compose-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.community-compose input,
.community-compose textarea,
.community-compose select,
.community-chat-input input,
.community-direct-start input {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 9px 10px;
  font: inherit;
}

.community-compose > input,
.community-compose textarea {
  width: 100%;
}

.community-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
  color: #334155;
}

.community-notice-list {
  display: grid;
  margin: 14px 0 8px;
  border: 1px solid #c7dbff;
  border-radius: 8px;
  overflow: hidden;
  background: #f6f9ff;
}

.community-notice-list article {
  min-height: 40px;
  display: grid;
  grid-template-columns: auto 70px minmax(0, 1fr) 90px 100px;
  gap: 10px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #dce8ff;
  color: #334155;
}

.community-notice-list article:last-child {
  border-bottom: 0;
}

.community-notice-list span,
.community-board-table td {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-board-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.community-board-table th,
.community-board-table td {
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
  text-align: left;
}

.community-board-table th {
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.community-category-badge {
  display: inline-flex;
  border-radius: 6px;
  background: #eef2ff;
  color: #3155d4;
  padding: 3px 8px;
  font-weight: 900;
  font-size: 12px;
}

.community-lower-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
  gap: 14px;
  align-items: stretch;
}

.community-card {
  min-width: 0;
  min-height: 430px;
  padding: 18px;
}

.community-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.community-card-head h2 {
  margin: 2px 0 0;
  font-size: 20px;
  line-height: 1.2;
}

.community-line-tabs,
.community-report-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.community-line-tabs button,
.community-report-tabs button {
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #fff;
  padding: 8px 10px;
  font-weight: 900;
  cursor: pointer;
}

.community-line-tabs button.active,
.community-report-tabs button.active {
  background: #eaf2ff;
  border-color: #8bbcff;
  color: #1257c6;
}

.community-group-stack {
  display: grid;
  gap: 10px;
  max-height: 360px;
  overflow: auto;
  padding-right: 2px;
}

.community-group-card {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: 8px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  padding: 12px;
  min-width: 0;
  min-height: 94px;
  box-shadow: none;
}

.community-group-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  min-width: 0;
}

.community-group-main strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-group-main span {
  display: block;
  min-width: 0;
  color: #64748b;
  line-height: 1.45;
  white-space: normal;
}

.community-group-card small {
  color: #64748b;
  line-height: 1.45;
}

.community-group-main b {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #eef6ff;
  color: #1257c6;
  padding: 4px 8px;
  font-size: 12px;
}

.community-avatar-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 10px 0;
}

.community-avatar-row button,
.community-avatar-row span {
  width: 30px;
  height: 30px;
  border: 1px solid #d6e1ee;
  border-radius: 50%;
  background: #f8fafc;
  font-size: 11px;
  font-weight: 900;
  color: #0f1f38;
  display: grid;
  place-items: center;
}

.community-avatar-row button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.community-group-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  justify-content: flex-end;
}

.community-group-actions button,
.community-group-modal header button {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f1f38;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}

.community-chat-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  margin-bottom: 10px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f7faff;
}

.community-chat-tabs button {
  min-height: 38px;
  border: 0;
  border-right: 1px solid #dbe5f0;
  background: transparent;
  color: #315174;
  font-weight: 900;
  cursor: pointer;
}

.community-chat-tabs button:last-child {
  border-right: 0;
}

.community-chat-tabs button.active {
  background: #eaf2ff;
  color: #1257c6;
  box-shadow: inset 0 -3px 0 #2563eb;
}

.community-chat-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  min-height: 340px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  overflow: hidden;
}

.community-room-list {
  display: grid;
  grid-template-rows: 1fr;
  background: #f7faff;
  border-right: 1px solid #dbe5f0;
  min-width: 0;
  min-height: 0;
}

.community-room-scroll {
  overflow-y: auto;
  padding: 10px;
  display: grid;
  gap: 8px;
  min-height: 0;
  height: 100%;
}

.community-direct-list {
  display: grid;
  gap: 8px;
}

.community-room-list button {
  width: 100%;
  border: 0;
  border-bottom: 1px solid #e2e8f0;
  background: transparent;
  padding: 12px;
  text-align: left;
  cursor: pointer;
}

.community-room-list button.active {
  background: #eaf2ff;
}

.community-room-list strong,
.community-room-list span {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-room-list span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.community-room-empty {
  margin: 0;
  padding: 18px 12px;
  color: #64748b;
  font-size: 13px;
  text-align: center;
}

.community-direct-start {
  display: grid;
  gap: 7px;
  padding: 10px;
  border-top: 1px solid #dbe5f0;
  background: #fff;
}

.community-direct-start > strong {
  font-size: 12px;
  color: #0f1f38;
}

.community-direct-start button,
.community-group-modal-grid button {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #fff;
  padding: 7px;
  cursor: pointer;
}

.community-direct-start button span,
.community-group-modal-grid button span {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eaf2ff;
  color: #1257c6;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 900;
}

.community-direct-start button b,
.community-group-modal-grid button b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-direct-start button small,
.community-group-modal-grid button small {
  color: #64748b;
  white-space: nowrap;
}

.community-chat-panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 340px;
  min-width: 0;
}

.community-chat-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.community-chat-panel header strong,
.community-chat-panel header span {
  display: block;
}

.community-chat-panel header span {
  color: #64748b;
  font-size: 12px;
  margin-top: 3px;
}

.community-message-list {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 12px;
  overflow: auto;
  max-height: 250px;
}

.community-message-list article {
  max-width: 78%;
  border-radius: 8px;
  background: #f1f5f9;
  padding: 9px 10px;
  word-break: break-word;
}

.community-message-list article.mine {
  margin-left: auto;
  background: #eaf2ff;
}

.community-message-list p {
  margin: 4px 0 0;
}

.community-message-list small {
  color: #64748b;
}

.community-chat-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 42px;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #e2e8f0;
}

.community-chat-input button,
.community-doc-generate {
  border: 0;
  border-radius: 8px;
  background: #1d5ee9;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 900;
  padding: 10px 12px;
}

.community-chat-input button:disabled,
.community-doc-generate:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.community-report-box {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  min-height: 310px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  overflow: hidden;
}

.community-report-box aside {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  background: #f7faff;
  border-right: 1px solid #dbe5f0;
}

.community-report-box aside strong {
  line-height: 1.35;
  word-break: keep-all;
}

.community-report-frame {
  width: 100%;
  height: 360px;
  border: 0;
  background: #fff;
}

.community-empty-row {
  color: #64748b;
  justify-content: center;
  text-align: center;
}

.community-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.46);
}

.community-group-modal {
  width: min(720px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  border-radius: 8px;
  background: #fff;
  padding: 20px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.22);
}

.community-group-modal header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.community-group-modal h2 {
  margin: 0;
}

.community-group-modal-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.community-group-modal-grid article {
  display: grid;
  align-content: start;
  gap: 8px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  padding: 12px;
}

.community-group-modal-grid h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

@media (max-width: 1500px) {
  .community-group-stack {
    max-height: none;
  }
}

@media (max-width: 900px) {
  .community-topbar,
  .community-topbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .community-search {
    width: 100%;
  }

  .community-board-tabs {
    gap: 14px;
    overflow-x: auto;
  }

  .community-chat-layout,
  .community-report-box,
  .community-group-modal-grid {
    grid-template-columns: 1fr;
  }

  .community-report-box aside {
    border-right: 0;
    border-bottom: 1px solid #dbe5f0;
  }
}
</style>
