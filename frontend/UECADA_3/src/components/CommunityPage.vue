<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import {
  CalendarDays,
  Download,
  FileText,
  LogOut,
  Megaphone,
  MessageSquare,
  Send,
  Users,
} from 'lucide-vue-next'
import {
  fetchChatMessages,
  fetchChatRooms,
  fetchFactoryReport,
  fetchLineGroups,
  sendChatMessage,
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
const selectedLineId = ref<string>('ALL')
const selectedRoomId = ref<number | null>(null)
const chatDraft = ref('')
const reportMarkdown = ref('')

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
  queryKey: computed(() => ['community', 'posts', selectedLineId.value]),
  queryFn: () => fetchPosts(undefined, selectedLineId.value === 'ALL' ? undefined : selectedLineId.value),
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

const selectedRoom = computed(() =>
  roomsQuery.data.value?.find((room) => room.chatRoomId === selectedRoomId.value) ?? null,
)

const visibleGroups = computed(() => lineGroupsQuery.data.value ?? [])
const visiblePosts = computed(() => postsQuery.data.value ?? [])
const visibleMessages = computed(() => messagesQuery.data.value ?? [])

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

const reportMutation = useMutation({
  mutationFn: fetchFactoryReport,
  onSuccess: (report) => {
    reportMarkdown.value = report.markdown
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

function downloadReport() {
  if (!reportMarkdown.value) return
  const blob = new Blob([reportMarkdown.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `uecada-factory-report-${new Date().toISOString().slice(0, 10)}.md`
  link.click()
  URL.revokeObjectURL(url)
}

function formatTime(iso: string | null | undefined) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : '-'
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
        <p>라인 그룹, 공지, 채팅, 공장 현황 자동 문서화를 관리합니다.</p>
      </div>
    </aside>

    <section class="dashboard-main community-dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">Community</p>
          <h1>커뮤니티</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            {{ new Date().toLocaleString('ko-KR') }}
          </span>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <section class="community-grid">
        <article class="dashboard-panel community-panel community-panel--groups">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Line Groups</p>
              <h2>라인별 그룹화</h2>
            </div>
            <Users :size="22" />
          </div>

          <div class="community-line-tabs">
            <button type="button" :class="{ active: selectedLineId === 'ALL' }" @click="selectedLineId = 'ALL'">
              전체
            </button>
            <button
              v-for="group in visibleGroups"
              :key="group.lineId"
              type="button"
              :class="{ active: selectedLineId === group.lineId }"
              @click="selectedLineId = group.lineId"
            >
              {{ group.lineName }}
            </button>
          </div>

          <div class="community-group-list">
            <article v-for="group in visibleGroups" :key="group.lineId" class="community-group-row">
              <strong>{{ group.lineName }}</strong>
              <p>관리자 {{ group.managers.length }}명 · 작업자 {{ group.operators.length }}명</p>
              <small>
                관리자: {{ group.managers.map((user) => user.userName).join(', ') || '-' }}
              </small>
              <small>
                작업자: {{ group.operators.map((user) => user.userName).join(', ') || '-' }}
              </small>
            </article>
          </div>
        </article>

        <article class="dashboard-panel community-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Board</p>
              <h2>게시판</h2>
            </div>
            <Megaphone :size="22" />
          </div>

          <form class="community-form" @submit.prevent="submitPost">
            <div class="community-form-row">
              <select v-model="postForm.category">
                <option v-for="opt in CATEGORY_OPTIONS" :key="opt.code" :value="opt.code">
                  {{ opt.label }}
                </option>
              </select>
              <select v-model="postForm.targetLineId">
                <option value="">전체 라인</option>
                <option v-for="group in visibleGroups" :key="group.lineId" :value="group.lineId">
                  {{ group.lineName }}
                </option>
              </select>
              <label class="community-check">
                <input v-model="postForm.notice" type="checkbox" />
                공지
              </label>
            </div>
            <input v-model="postForm.title" type="text" placeholder="제목" />
            <textarea v-model="postForm.content" rows="3" placeholder="작업자에게 전달할 내용을 입력하세요."></textarea>
            <button class="primary-action" type="submit" :disabled="createPostMutation.isPending.value">
              <Megaphone :size="16" />
              <span>{{ createPostMutation.isPending.value ? '등록 중…' : '공지 등록' }}</span>
            </button>
          </form>

          <div class="community-post-list">
            <article v-for="post in visiblePosts" :key="post.postId" class="community-post">
              <div>
                <span>{{ categoryLabel(post.category) }}</span>
                <span>{{ post.targetLineId || '전체' }}</span>
                <time>{{ formatTime(post.createdAt) }}</time>
              </div>
              <strong>{{ post.title }}</strong>
              <p>{{ post.content }}</p>
            </article>
          </div>
        </article>

        <article class="dashboard-panel community-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Work Chat</p>
              <h2>채팅</h2>
            </div>
            <MessageSquare :size="22" />
          </div>

          <div class="community-room-tabs">
            <button
              v-for="room in roomsQuery.data.value ?? []"
              :key="room.chatRoomId"
              type="button"
              :class="{ active: selectedRoomId === room.chatRoomId }"
              @click="selectedRoomId = room.chatRoomId"
            >
              {{ room.roomName }}
            </button>
          </div>

          <div class="community-chat-box">
            <article
              v-for="message in visibleMessages"
              :key="message.messageId"
              :class="['community-message', { mine: message.senderUserId === currentUser.userId }]"
            >
              <strong>{{ message.senderUserId }}</strong>
              <p>{{ message.messageContent }}</p>
              <time>{{ formatTime(message.sentAt) }}</time>
            </article>
            <p v-if="!visibleMessages.length" class="community-empty">아직 메시지가 없습니다.</p>
          </div>

          <form class="community-chat-input" @submit.prevent="submitMessage">
            <input
              v-model="chatDraft"
              type="text"
              :placeholder="selectedRoom ? `${selectedRoom.roomName}에 메시지 입력` : '채팅방 선택'"
            />
            <button type="submit" class="primary-action" :disabled="sendMessageMutation.isPending.value">
              <Send :size="16" />
            </button>
          </form>
        </article>

        <article class="dashboard-panel community-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Auto Documentation</p>
              <h2>자동 문서화</h2>
            </div>
            <FileText :size="22" />
          </div>

          <div class="community-report-actions">
            <button class="primary-action" type="button" @click="reportMutation.mutate()">
              <FileText :size="16" />
              <span>{{ reportMutation.isPending.value ? '작성 중…' : '공장 현황 보고서 생성' }}</span>
            </button>
            <button class="ghost-button" type="button" :disabled="!reportMarkdown" @click="downloadReport">
              <Download :size="16" />
              <span>저장</span>
            </button>
          </div>

          <pre class="community-report-preview">{{ reportMarkdown || '버튼을 누르면 설비 가동상태, OEE, 알람, 이상설비, 라인 현황, 생산률, ESG 대응 데이터가 보고서로 생성됩니다.' }}</pre>
        </article>
      </section>
    </section>
  </main>
</template>

<style scoped>
.community-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(420px, 1.1fr);
  gap: 16px;
  align-items: start;
}
.community-panel {
  min-height: 360px;
}
.community-panel--groups {
  min-height: 300px;
}
.community-line-tabs,
.community-room-tabs,
.community-form-row,
.community-report-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.community-line-tabs button,
.community-room-tabs button {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  font-weight: 800;
  color: #334155;
  cursor: pointer;
}
.community-line-tabs button.active,
.community-room-tabs button.active {
  background: #0f4c81;
  color: #fff;
  border-color: #0f4c81;
}
.community-group-list,
.community-post-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.community-group-row,
.community-post {
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.community-group-row p,
.community-group-row small,
.community-post p {
  margin: 4px 0 0;
  color: #64748b;
}
.community-group-row small {
  display: block;
}
.community-form {
  display: grid;
  gap: 9px;
}
.community-form input,
.community-form textarea,
.community-form select,
.community-chat-input input {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 9px 10px;
  font: inherit;
}
.community-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
  color: #334155;
}
.community-post div {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}
.community-chat-box {
  height: 250px;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 8px;
  margin: 12px 0;
  padding: 10px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #f8fafc;
}
.community-message {
  max-width: 78%;
  padding: 9px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
}
.community-message.mine {
  margin-left: auto;
  background: #eef6ff;
  border-color: #b9dcff;
}
.community-message p {
  margin: 3px 0;
}
.community-message time {
  font-size: 11px;
  color: #94a3b8;
}
.community-chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
.community-report-preview {
  margin: 12px 0 0;
  min-height: 260px;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
  color: #1e293b;
  font: 13px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.community-empty {
  color: #64748b;
  text-align: center;
}
@media (max-width: 1180px) {
  .community-grid {
    grid-template-columns: 1fr;
  }
}
</style>
