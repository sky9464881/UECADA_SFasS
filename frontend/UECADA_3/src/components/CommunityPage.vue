<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import {
  Bell,
  ChevronDown,
  Download,
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
const selectedLineId = ref<string>('ALL')
const selectedRoomId = ref<number | null>(null)
const selectedReportType = ref<FactoryReportType>('heat_safety')
const chatDraft = ref('')
const searchText = ref('')
const reportByType = ref<Partial<Record<FactoryReportType, FactoryReport>>>({})
const showPostComposer = ref(false)

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
  staleTime: 30_000,
  refetchInterval: 10_000,
})

const postsQuery = useQuery({
  queryKey: computed(() => ['community', 'posts', selectedBoardCategory.value, selectedLineId.value]),
  queryFn: () => fetchPosts(
    selectedBoardCategory.value,
    selectedLineId.value === 'ALL' ? undefined : selectedLineId.value,
  ),
  staleTime: 5_000,
  refetchInterval: 5_000,
})

const roomsQuery = useQuery({
  queryKey: computed(() => ['community', 'chat-rooms', currentUser.value.userId]),
  queryFn: () => fetchChatRooms(currentUser.value.userId),
  staleTime: 5_000,
  refetchInterval: 5_000,
})

const messagesQuery = useQuery({
  queryKey: computed(() => ['community', 'chat-messages', selectedRoomId.value, currentUser.value.userId]),
  queryFn: () => selectedRoomId.value
    ? fetchChatMessages(selectedRoomId.value, currentUser.value.userId)
    : Promise.resolve([]),
  enabled: computed(() => selectedRoomId.value != null),
  staleTime: 1_000,
  refetchInterval: 2_000,
})

watch(
  () => roomsQuery.data.value,
  (rooms) => {
    if (!rooms?.length) return
    if (!selectedRoomId.value || !rooms.some((room) => room.chatRoomId === selectedRoomId.value)) {
      selectedRoomId.value = rooms[0].chatRoomId
    }
  },
  { immediate: true },
)

watch(selectedBoardCategory, (category) => {
  postForm.category = category
  postForm.notice = category === 'NOTICE'
})

const groups = computed(() => lineGroupsQuery.data.value ?? [])
const allUsers = computed(() =>
  groups.value.flatMap((group) => [...group.managers, ...group.operators]),
)
const posts = computed(() => postsQuery.data.value ?? [])
const pinnedPosts = computed(() => posts.value.filter((post) => post.notice).slice(0, 3))
const tablePosts = computed(() => posts.value.filter((post) => !post.notice).slice(0, 8))
const rooms = computed(() => roomsQuery.data.value ?? [])
const selectedRoom = computed(() => rooms.value.find((room) => room.chatRoomId === selectedRoomId.value) ?? null)
const messages = computed(() => messagesQuery.data.value ?? [])
const selectedReport = computed(() => reportByType.value[selectedReportType.value] ?? null)

const filteredGroups = computed(() =>
  selectedLineId.value === 'ALL'
    ? groups.value
    : groups.value.filter((group) => group.lineId === selectedLineId.value),
)

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

function directChat(user: LineGroupUser) {
  if (user.userId === currentUser.value.userId) return
  directRoomMutation.mutate(user.userId)
}

function canDirectChat(user: LineGroupUser): boolean {
  if (user.userId === currentUser.value.userId) return false
  if (currentUser.value.roleName?.toUpperCase() === 'ADMIN') return true
  return !!currentUser.value.lineId && currentUser.value.lineId === user.lineId
}

function formatDate(iso: string | null | undefined) {
  return iso ? iso.replace('T', ' ').slice(0, 10) : '-'
}

function formatTime(iso: string | null | undefined) {
  return iso ? iso.replace('T', ' ').slice(11, 16) : '-'
}

function initials(name: string): string {
  return name.trim().slice(-2) || '사용'
}
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar" aria-label="주요 메뉴">
      <RouterLink class="dashboard-brand" :to="{ name: 'dashboard' }">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스카다</small>
        </span>
      </RouterLink>

      <nav class="dashboard-nav">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.to">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <span>{{ currentUser.roleName }}</span>
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
          <button type="button" class="community-bell" aria-label="알림">
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

      <section class="community-board-panel">
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
            <label>
              <input v-model="postForm.notice" type="checkbox" />
              공지
            </label>
          </div>
          <input v-model="postForm.title" type="text" placeholder="제목" />
          <textarea v-model="postForm.content" rows="3" placeholder="내용을 입력하세요."></textarea>
          <button class="primary-action" type="submit" :disabled="createPostMutation.isPending.value">
            <Megaphone :size="16" />
            <span>{{ createPostMutation.isPending.value ? '등록 중' : '등록' }}</span>
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
        <article class="community-card">
          <header class="community-card-head">
            <div>
              <p class="panel-kicker">1) Line Group</p>
              <h2>라인 그룹</h2>
            </div>
            <Users :size="22" />
          </header>

          <div class="community-line-tabs">
            <button type="button" :class="{ active: selectedLineId === 'ALL' }" @click="selectedLineId = 'ALL'">전체</button>
            <button
              v-for="group in groups"
              :key="group.lineId"
              type="button"
              :class="{ active: selectedLineId === group.lineId }"
              @click="selectedLineId = group.lineId"
            >
              {{ group.lineName }}
            </button>
          </div>

          <div class="community-group-stack">
            <article v-for="group in filteredGroups" :key="group.lineId" class="community-group-card">
              <div>
                <strong>{{ group.lineName }}</strong>
                <span>관리자 {{ group.managers.length }}명 · 작업자 {{ group.operators.length }}명</span>
              </div>
              <div class="community-avatar-row">
                <button
                  v-for="user in [...group.managers, ...group.operators]"
                  :key="user.userId"
                  type="button"
                  :title="`${user.userName} 1:1 채팅`"
                  :disabled="!canDirectChat(user)"
                  @click="directChat(user)"
                >
                  {{ initials(user.userName) }}
                </button>
              </div>
              <small>라인 사용자만 라인 채팅에 접근하고, 관리자는 전체 1:1 채팅을 생성할 수 있습니다.</small>
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

          <div class="community-chat-layout">
            <aside class="community-room-list">
              <button
                v-for="room in rooms"
                :key="room.chatRoomId"
                type="button"
                :class="{ active: selectedRoomId === room.chatRoomId }"
                @click="selectedRoomId = room.chatRoomId"
              >
                <strong>{{ room.roomName }}</strong>
                <span>{{ room.roomType === 'DIRECT' ? '1:1 채팅' : '라인 채팅' }}</span>
              </button>
            </aside>

            <section class="community-chat-panel">
              <header>
                <strong>{{ selectedRoom?.roomName || '채팅방 선택' }}</strong>
                <span>{{ messages.length }}개 메시지</span>
              </header>
              <div class="community-message-list">
                <article
                  v-for="message in messages"
                  :key="message.messageId"
                  :class="{ mine: message.senderUserId === currentUser.userId }"
                >
                  <small>{{ message.senderUserId }} · {{ formatTime(message.sentAt) }}</small>
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
                <button type="submit" :disabled="sendMessageMutation.isPending.value || !selectedRoomId">
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
            <button class="community-doc-generate" type="button" @click="generateReport">
              <Sparkles :size="16" />
              <span>{{ reportMutation.isPending.value ? '생성 중' : '자동 문서화 생성' }}</span>
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
              <strong>{{ reportTabs.find((tab) => tab.type === selectedReportType)?.label }}</strong>
              <span>{{ selectedReport ? formatDate(selectedReport.generatedAt) : '생성 대기' }}</span>
              <button class="ghost-button" type="button" :disabled="!selectedReport" @click="downloadReport">
                <Download :size="16" />
                <span>저장</span>
              </button>
            </aside>
            <pre>{{ selectedReport?.markdown || '탭을 선택한 뒤 자동 문서화 생성 버튼을 누르면 현재 공장 버퍼 데이터가 지정된 보고서 형식으로 작성됩니다.' }}</pre>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>

<style scoped>
.community-main {
  gap: 14px;
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
  padding: 0 20px 20px;
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
.community-chat-input input {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 9px 10px;
  font: inherit;
}

.community-notice-list {
  display: grid;
  margin: 16px 0 8px;
  border: 1px solid #c7dbff;
  border-radius: 8px;
  overflow: hidden;
  background: #f6f9ff;
}

.community-notice-list article {
  min-height: 40px;
  display: grid;
  grid-template-columns: auto 60px 1fr 90px 100px;
  gap: 10px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #dce8ff;
  color: #334155;
}

.community-notice-list article:last-child {
  border-bottom: 0;
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
  grid-template-columns: minmax(300px, 0.9fr) minmax(420px, 1.1fr) minmax(420px, 1fr);
  gap: 14px;
  align-items: stretch;
}

.community-card {
  min-height: 390px;
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
}

.community-group-card {
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  padding: 12px;
}

.community-group-card div:first-child {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.community-group-card span,
.community-group-card small {
  color: #64748b;
}

.community-avatar-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 10px 0;
}

.community-avatar-row button {
  width: 30px;
  height: 30px;
  border: 1px solid #d6e1ee;
  border-radius: 50%;
  background: #f8fafc;
  font-size: 11px;
  font-weight: 900;
  color: #0f1f38;
}

.community-avatar-row button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.community-chat-layout {
  display: grid;
  grid-template-columns: 190px 1fr;
  min-height: 310px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  overflow: hidden;
}

.community-room-list {
  display: grid;
  align-content: start;
  background: #f7faff;
  border-right: 1px solid #dbe5f0;
}

.community-room-list button {
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
}

.community-room-list span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.community-chat-panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 310px;
}

.community-chat-panel header {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.community-message-list {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 12px;
  overflow: auto;
  max-height: 220px;
}

.community-message-list article {
  max-width: 78%;
  border-radius: 8px;
  background: #f1f5f9;
  padding: 9px 10px;
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
  grid-template-columns: 1fr 42px;
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

.community-report-box {
  display: grid;
  grid-template-columns: 150px 1fr;
  min-height: 290px;
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

.community-report-box pre {
  margin: 0;
  padding: 14px;
  overflow: auto;
  max-height: 320px;
  white-space: pre-wrap;
  font: 13px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace;
  color: #1e293b;
}

.community-empty-row {
  color: #64748b;
  justify-content: center;
  text-align: center;
}

@media (max-width: 1420px) {
  .community-lower-grid {
    grid-template-columns: 1fr;
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

  .community-chat-layout,
  .community-report-box {
    grid-template-columns: 1fr;
  }
}
</style>
