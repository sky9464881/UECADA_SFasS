<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import type { CSSProperties } from 'vue'
import { RouterLink } from 'vue-router'
import {
  CalendarDays,
  ChevronDown,
  LogOut,
  Megaphone,
  MessageSquare,
  Paperclip,
  Pin,
  Send,
  Users,
  X,
} from 'lucide-vue-next'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { usePosts } from '@/composables/usePosts'
import { CATEGORY_OPTIONS, categoryLabel } from '@/types/post'

const { navItems } = useAppNav()
const logout = useLogout()

const { posts: backendPosts, isPending: postsPending, isError: postsError, error: postsErrorObj, create: createPostMut } = usePosts()

function formatPostTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 16)
}

const notices = computed(() =>
  backendPosts.value.map((p) => ({
    id: p.postId,
    category: categoryLabel(p.category),
    title: p.title,
    author: p.authorUserId,
    target: '전체', // 백엔드에 라인별 대상 개념 없음
    time: formatPostTime(p.createdAt),
    pinned: (p.category ?? '').toUpperCase() === 'NOTICE',
  })),
)

const showCreatePostModal = ref(false)
const createPostForm = reactive({
  authorUserId: 'U001',
  title: '',
  content: '',
  category: 'NOTICE',
})
const createPostError = ref('')

function openCreatePostModal() {
  createPostError.value = ''
  Object.assign(createPostForm, {
    authorUserId: 'U001',
    title: '',
    content: '',
    category: 'NOTICE',
  })
  showCreatePostModal.value = true
}

function closeCreatePostModal() {
  showCreatePostModal.value = false
}

async function submitCreatePost() {
  createPostError.value = ''
  if (!createPostForm.authorUserId || !createPostForm.title || !createPostForm.content) {
    createPostError.value = '작성자·제목·내용은 필수입니다.'
    return
  }
  try {
    await createPostMut.mutateAsync({
      authorUserId: createPostForm.authorUserId,
      title: createPostForm.title,
      content: createPostForm.content,
      category: createPostForm.category,
    })
    closeCreatePostModal()
  } catch (e: unknown) {
    const errorObj = e as { response?: { data?: { message?: string } }; message?: string }
    createPostError.value = errorObj?.response?.data?.message || errorObj?.message || '게시글 작성 실패'
  }
}

/** 라인별 그룹(요약) — 드롭다운 선택 시 상단에 표시 */
const lineGroups = [
  {
    lineKey: 'line-a',
    name: '라인 A · 주조',
    manager: '박주조',
    members: ['OP-1042', 'OP-1038', 'OP-1024', 'OP-1011'],
    task: 'CAST-02 온도 알람 확인',
  },
  {
    lineKey: 'line-b',
    name: '라인 B · 가공',
    manager: '이가공',
    members: ['OP-1130', 'OP-1125', 'OP-1104', 'MT-014'],
    task: 'MACH-11 공구 교체 준비',
  },
  {
    lineKey: 'line-c',
    name: '라인 C · 검사',
    manager: '한검사',
    members: ['OP-1187', 'OP-1171', 'QC-022', 'QC-018', 'OP-1008'],
    task: '치수 편차 샘플 재측정 · 압입하중 편차 확인',
  },
]

const lineScopeOptions = [
  { id: 'all', label: '전체 라인', targetLabel: null },
  { id: 'line-a', label: '라인 A', targetLabel: '라인 A' },
  { id: 'line-b', label: '라인 B', targetLabel: '라인 B' },
  { id: 'line-c', label: '라인 C', targetLabel: '라인 C' },
]

// 백엔드에 게시글의 target(라인)·pinned 개념이 없어 라인 필터링은 채팅에만 적용.

const chatMessages = [
  {
    user: '김관리',
    role: '관리자',
    line: '전체',
    time: '12:35',
    message: 'CAST-02 알람 조치 상황 공유 부탁드립니다.',
    mine: false,
  },
  {
    user: '박주조',
    role: '작업자',
    line: '라인 A',
    time: '12:36',
    message: '용탕온도 확인했고 센서값 재확인 중입니다.',
    mine: false,
  },
  {
    user: '김관리',
    role: '관리자',
    line: '전체',
    time: '12:38',
    message: '13시 전까지 금형온도도 같이 확인해서 결과 남겨주세요.',
    mine: true,
  },
  {
    user: '한검사',
    role: '작업자',
    line: '라인 C',
    time: '12:39',
    message: '검사 치수 편차 알람은 샘플 10개 추가 측정했습니다.',
    mine: false,
  },
  {
    user: '이가공',
    role: '작업자',
    line: '라인 B',
    time: '12:05',
    message: 'MACH-11 공구 교체 준비 완료했습니다.',
    mine: false,
  },
  {
    user: '이가공',
    role: '작업자',
    line: '라인 B',
    time: '11:42',
    message: '라인 B 가공 라인 스핀들 진동값 정상 범위입니다.',
    mine: false,
  },
  {
    user: '박주조',
    role: '작업자',
    line: '라인 A',
    time: '11:20',
    message: '주조 CT 편차 원인 분석 중입니다.',
    mine: false,
  },
]

const selectedLineScope = ref('all')
const lineDropdownOpen = ref(false)
const lineDropdownAnchorRef = ref<HTMLElement | null>(null)
/** Teleport된 드롭다운 패널 위치 (뷰포트 기준 fixed) */
const portalDropdownStyle = ref<CSSProperties>({})

const selectedScopeOption = computed(
  () => lineScopeOptions.find((o) => o.id === selectedLineScope.value) ?? lineScopeOptions[0],
)

const activeLineGroup = computed(() =>
  selectedLineScope.value === 'all'
    ? null
    : lineGroups.find((g) => g.lineKey === selectedLineScope.value) ?? null,
)

const filteredNotices = computed(() => {
  // 백엔드 게시글에 라인별 대상이 없으므로 전체 게시글을 그대로 노출.
  return notices.value
})

const filteredChatMessages = computed(() => {
  const opt = selectedScopeOption.value
  if (!opt.targetLabel) return chatMessages
  const t = opt.targetLabel
  return chatMessages.filter((m) => m.line === '전체' || m.line === t)
})

function syncPortalDropdownPosition() {
  const anchor = lineDropdownAnchorRef.value
  if (!anchor) return
  const r = anchor.getBoundingClientRect()
  const margin = 10
  const vw = window.innerWidth
  const vh = window.innerHeight
  const panelMaxW = 520
  const width = Math.min(panelMaxW, Math.max(r.width, 300), vw - margin * 2)
  let left = r.left
  if (left + width > vw - margin) {
    left = Math.max(margin, vw - margin - width)
  }
  const top = r.bottom + 6
  const maxHeight = Math.max(220, Math.min(vh * 0.72, vh - top - margin))

  portalDropdownStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${left}px`,
    width: `${width}px`,
    maxHeight: `${maxHeight}px`,
  }
}

function toggleLineDropdown(ev: Event) {
  ev.stopPropagation()
  lineDropdownOpen.value = !lineDropdownOpen.value
}

function selectLineScope(id: string) {
  selectedLineScope.value = id
}

function closeLineDropdown() {
  lineDropdownOpen.value = false
}

function onEscapeKey(ev: KeyboardEvent) {
  if (ev.key === 'Escape') closeLineDropdown()
}

function onScrollOrResize() {
  if (lineDropdownOpen.value) syncPortalDropdownPosition()
}

watch(lineDropdownOpen, async (open) => {
  if (open) {
    await nextTick()
    syncPortalDropdownPosition()
    document.addEventListener('keydown', onEscapeKey)
    window.addEventListener('resize', onScrollOrResize)
    document.addEventListener('scroll', onScrollOrResize, true)
  } else {
    document.removeEventListener('keydown', onEscapeKey)
    window.removeEventListener('resize', onScrollOrResize)
    document.removeEventListener('scroll', onScrollOrResize, true)
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', onEscapeKey)
  window.removeEventListener('resize', onScrollOrResize)
  document.removeEventListener('scroll', onScrollOrResize, true)
})
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
        <span>관리자</span>
        <strong>김관리</strong>
        <p>라인별 작업자 그룹, 관리자 게시판, 업무지시 채팅 관리</p>
      </div>
    </aside>

    <section class="dashboard-main community-dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles community-header-titles">
          <div class="community-header-title-block">
            <p class="dashboard-kicker">Community</p>
            <div class="community-title-row">
              <h1>커뮤니티</h1>
              <div ref="lineDropdownAnchorRef" class="community-line-dropdown">
                <button
                  type="button"
                  class="community-line-dropdown-trigger"
                  :aria-expanded="lineDropdownOpen"
                  aria-haspopup="dialog"
                  aria-controls="community-line-dropdown-portal-panel"
                  @click="toggleLineDropdown"
                >
                  <Users :size="18" />
                  <span class="community-line-dropdown-label">
                    <span class="community-line-dropdown-primary">라인별 그룹화</span>
                    <span class="community-line-dropdown-secondary">{{ selectedScopeOption.label }}</span>
                  </span>
                  <ChevronDown
                    class="community-line-dropdown-chevron"
                    :class="{ 'community-line-dropdown-chevron--open': lineDropdownOpen }"
                    :size="18"
                    :stroke-width="2.4"
                  />
                </button>
              </div>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <button class="primary-action" type="button" @click="openCreatePostModal">
            <Megaphone :size="17" />
            <span>알림 작성</span>
          </button>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <!-- 드롭다운을 body로 포탈 렌더링 (overflow·z-index 이슈 회피, 트리거 기준 위치) -->
      <Teleport to="body">
        <div
          v-if="lineDropdownOpen"
          class="community-line-dropdown-portal"
          role="presentation"
        >
          <div
            class="community-line-dropdown-portal-backdrop"
            aria-hidden="true"
            @click="closeLineDropdown"
          />
          <div
            id="community-line-dropdown-portal-panel"
            class="community-line-overlay-panel community-line-dropdown-portal-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="community-line-overlay-title"
            :style="portalDropdownStyle"
            @click.stop
          >
            <header class="community-line-overlay-head">
              <div>
                <p class="community-line-overlay-kicker">라인 범위</p>
                <h2 id="community-line-overlay-title">라인별 그룹화</h2>
                <p class="community-line-overlay-desc">
                  게시판·채팅에 표시할 라인 범위를 선택합니다.
                </p>
              </div>
              <button
                type="button"
                class="community-line-overlay-close"
                aria-label="닫기"
                @click="closeLineDropdown"
              >
                <X :size="20" :stroke-width="2.2" />
              </button>
            </header>

            <section class="community-line-overlay-section" aria-label="표시 범위">
              <h3 class="community-line-overlay-section-title">표시 범위</h3>
              <ul
                id="community-line-scope-list"
                class="community-line-overlay-scope-list"
                role="listbox"
                :aria-label="'현재 선택: ' + selectedScopeOption.label"
              >
                <li v-for="opt in lineScopeOptions" :key="opt.id" role="none">
                  <button
                    type="button"
                    role="option"
                    class="community-line-dropdown-item"
                    :aria-selected="selectedLineScope === opt.id"
                    @click="selectLineScope(opt.id)"
                  >
                    {{ opt.label }}
                  </button>
                </li>
              </ul>
            </section>

            <footer class="community-line-overlay-footer">
              <button type="button" class="primary-action" @click="closeLineDropdown">
                확인
              </button>
            </footer>
          </div>
        </div>
      </Teleport>

      <section class="community-workspace" aria-label="커뮤니티 기능 영역">
        <div class="community-feature-pane community-feature-pane--full">
          <div
            v-if="activeLineGroup"
            class="community-line-context-bar"
            :aria-label="`${activeLineGroup.name} 요약`"
          >
            <div>
              <strong>{{ activeLineGroup.name }}</strong>
              <span>담당 {{ activeLineGroup.manager }} · {{ activeLineGroup.members.length }}명</span>
            </div>
            <p>{{ activeLineGroup.task }}</p>
          </div>

          <div class="community-board-chat-split" aria-label="게시판과 채팅">
            <article class="dashboard-panel board-panel community-feature-panel community-split-half">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Admin Board</p>
                  <h2>게시판</h2>
                  <p v-if="selectedScopeOption.targetLabel" class="community-scope-sub">
                    {{ selectedScopeOption.label }} 관련 글
                  </p>
                  <p v-else class="community-scope-sub">전체 라인</p>
                </div>
                <span class="section-note">관리자 작성</span>
              </div>

              <div class="notice-list simple">
                <p v-if="postsPending" class="community-empty-inline">게시글을 불러오는 중…</p>
                <p v-else-if="postsError" class="community-empty-inline" style="color: #dc2626">
                  불러오기 실패: {{ postsErrorObj?.message ?? '알 수 없는 오류' }}
                </p>
                <template v-else-if="filteredNotices.length">
                  <article v-for="notice in filteredNotices" :key="notice.id">
                    <div class="notice-icon">
                      <Pin v-if="notice.pinned" :size="17" />
                      <Megaphone v-else :size="17" />
                    </div>
                    <div>
                      <div class="notice-meta">
                        <span>{{ notice.category }}</span>
                        <span>{{ notice.target }}</span>
                        <time>{{ notice.time }}</time>
                      </div>
                      <strong>{{ notice.title }}</strong>
                      <p>{{ notice.author }} 작성</p>
                    </div>
                  </article>
                </template>
                <p v-else class="community-empty-inline">표시할 게시글이 없습니다.</p>
              </div>
            </article>

            <article class="dashboard-panel chat-panel community-feature-panel community-split-half">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Work Chat</p>
                  <h2>채팅 <small style="color: #94a3b8; font-weight: 500; font-size: 12px">· 데모 UI (백엔드 미연결)</small></h2>
                  <p v-if="selectedScopeOption.targetLabel" class="community-scope-sub">
                    {{ selectedScopeOption.label }} · 전체 공지 포함
                  </p>
                  <p v-else class="community-scope-sub">전체 라인</p>
                </div>
                <MessageSquare :size="22" />
              </div>

              <div class="chat-message-list">
                <template v-if="filteredChatMessages.length">
                  <article
                    v-for="message in filteredChatMessages"
                    :key="`${message.user}-${message.time}-${message.message.slice(0, 12)}`"
                    :class="{ mine: message.mine }"
                  >
                    <div class="chat-avatar">{{ message.user.slice(0, 1) }}</div>
                    <div class="chat-bubble">
                      <div class="chat-meta">
                        <strong>{{ message.user }}</strong>
                        <span>{{ message.role }} · {{ message.line }} · {{ message.time }}</span>
                      </div>
                      <p>{{ message.message }}</p>
                    </div>
                  </article>
                </template>
                <p v-else class="community-empty-inline community-empty-inline--chat">표시할 메시지가 없습니다.</p>
              </div>

              <div class="chat-input-row">
                <button type="button" title="첨부">
                  <Paperclip :size="17" />
                </button>
                <input type="text" value="구체적인 업무지시를 입력하세요" />
                <button type="button" title="전송">
                  <Send :size="17" />
                </button>
              </div>
            </article>
          </div>
        </div>
      </section>
    </section>

    <div v-if="showCreatePostModal" class="post-create-modal" @click.self="closeCreatePostModal">
      <div class="post-create-modal__card">
        <header class="post-create-modal__head">
          <h3>알림 작성</h3>
          <button type="button" class="icon-link" aria-label="닫기" @click="closeCreatePostModal">
            <X :size="18" />
          </button>
        </header>
        <form class="post-create-modal__body" @submit.prevent="submitCreatePost">
          <label>
            <span>작성자 ID *</span>
            <input v-model="createPostForm.authorUserId" type="text" maxlength="20" required />
          </label>
          <label>
            <span>카테고리</span>
            <select v-model="createPostForm.category">
              <option v-for="opt in CATEGORY_OPTIONS" :key="opt.code" :value="opt.code">
                {{ opt.label }}
              </option>
            </select>
          </label>
          <label>
            <span>제목 *</span>
            <input v-model="createPostForm.title" type="text" maxlength="200" required />
          </label>
          <label>
            <span>내용 *</span>
            <textarea v-model="createPostForm.content" rows="5" required></textarea>
          </label>

          <p v-if="createPostError" class="post-create-modal__error">{{ createPostError }}</p>

          <footer class="post-create-modal__foot">
            <button type="button" class="ghost-button" @click="closeCreatePostModal">취소</button>
            <button type="submit" class="primary-action" :disabled="createPostMut.isPending.value">
              {{ createPostMut.isPending.value ? '저장 중…' : '저장' }}
            </button>
          </footer>
        </form>
      </div>
    </div>
  </main>
</template>

<style scoped>
.post-create-modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}
.post-create-modal__card {
  width: min(520px, 92vw);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
}
.post-create-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}
.post-create-modal__head h3 {
  margin: 0;
  font-size: 16px;
}
.post-create-modal__body {
  padding: 18px 20px;
  display: grid;
  gap: 12px;
}
.post-create-modal__body label {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: #475569;
}
.post-create-modal__body input,
.post-create-modal__body select,
.post-create-modal__body textarea {
  padding: 8px 10px;
  border: 1px solid #cbd5f5;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
}
.post-create-modal__body textarea {
  resize: vertical;
  min-height: 100px;
}
.post-create-modal__error {
  margin: 0;
  padding: 8px 10px;
  background: #fef2f2;
  color: #b91c1c;
  border-radius: 8px;
  font-size: 13px;
}
.post-create-modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
