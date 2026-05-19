<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { X } from 'lucide-vue-next'
import {
  fetchSecurityQuestion,
  findLoginId,
  login as loginApi,
  resetPassword,
  signup,
} from '@/api/authApi'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const modalTitle = {
  findId: '아이디 찾기',
  findPw: '비밀번호 찾기',
  signup: '회원가입',
} as const

type ModalId = keyof typeof modalTitle

const activeModal = ref<ModalId | null>(null)
const loginForm = reactive({ loginId: 'admin', password: 'secret' })
const signupForm = reactive({
  userId: '',
  loginId: '',
  lineId: '',
  userName: '',
  email: '',
  roleName: 'OPERATOR',
  password: '',
  confirmPassword: '',
  securityQuestion: '초기 보안 답변은?',
  securityAnswer: '',
})
const findIdForm = reactive({ userName: '', email: '', securityAnswer: '' })
const resetForm = reactive({ loginId: '', securityAnswer: '', newPassword: '', confirmPassword: '' })
const securityQuestion = ref('')
const loginError = ref('')
const modalMessage = ref('')
const isSubmitting = ref(false)

async function submitLogin() {
  loginError.value = ''
  isSubmitting.value = true
  try {
    const user = await loginApi(loginForm)
    auth.login(user)
    const raw = route.query.redirect
    const redirect = Array.isArray(raw) ? raw[0] : raw
    if (redirect && typeof redirect === 'string') {
      await router.push(redirect)
      return
    }
    await router.push({ name: 'dashboard' })
  } catch (error: unknown) {
    loginError.value = (error as { response?: { status?: number } })?.response?.status === 401
      ? '아이디 또는 비밀번호가 올바르지 않습니다.'
      : '로그인에 실패했습니다. 백엔드 상태를 확인하세요.'
  } finally {
    isSubmitting.value = false
  }
}

const openModal = (id: ModalId) => {
  modalMessage.value = ''
  securityQuestion.value = ''
  activeModal.value = id
}

const closeModal = () => {
  activeModal.value = null
  modalMessage.value = ''
  securityQuestion.value = ''
}

async function submitFindId() {
  modalMessage.value = ''
  try {
    const result = await findLoginId(findIdForm)
    modalMessage.value = `등록된 로그인 ID: ${result.loginId}`
  } catch {
    modalMessage.value = '일치하는 계정 또는 보안 답변을 찾지 못했습니다.'
  }
}

async function loadSecurityQuestion() {
  modalMessage.value = ''
  securityQuestion.value = ''
  if (!resetForm.loginId) {
    modalMessage.value = '아이디를 먼저 입력하세요.'
    return
  }
  try {
    const result = await fetchSecurityQuestion(resetForm.loginId)
    securityQuestion.value = result.securityQuestion || '등록된 보안 질문이 없습니다.'
  } catch {
    modalMessage.value = '아이디에 해당하는 계정을 찾지 못했습니다.'
  }
}

async function submitResetPassword() {
  modalMessage.value = ''
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    modalMessage.value = '새 비밀번호와 확인값이 다릅니다.'
    return
  }
  try {
    await resetPassword({
      loginId: resetForm.loginId,
      securityAnswer: resetForm.securityAnswer,
      newPassword: resetForm.newPassword,
    })
    modalMessage.value = '비밀번호가 변경되었습니다. 새 비밀번호로 로그인하세요.'
  } catch {
    modalMessage.value = '보안 답변 확인 또는 비밀번호 변경에 실패했습니다.'
  }
}

async function submitSignup() {
  modalMessage.value = ''
  if (signupForm.password !== signupForm.confirmPassword) {
    modalMessage.value = '비밀번호와 확인값이 다릅니다.'
    return
  }
  try {
    await signup({
      userId: signupForm.userId,
      loginId: signupForm.loginId,
      lineId: signupForm.lineId || null,
      userName: signupForm.userName,
      email: signupForm.email || undefined,
      roleName: signupForm.roleName,
      password: signupForm.password,
      securityQuestion: signupForm.securityQuestion,
      securityAnswer: signupForm.securityAnswer,
    })
    modalMessage.value = '가입이 완료되었습니다. 생성한 계정으로 로그인하세요.'
  } catch {
    modalMessage.value = '가입에 실패했습니다. 사용자 ID 또는 로그인 ID 중복을 확인하세요.'
  }
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') closeModal()
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <main class="login-page">
    <section class="visual-area" aria-label="설비 관제 현황">
      <div class="brand-mark">
        <span class="brand-symbol">U</span>
        <span class="brand-copy">
          <strong>우리들의 스카다</strong>
          <small>유익하다</small>
        </span>
      </div>

      <div class="command-copy">
        <p class="eyebrow">설비통합관제시스템</p>
        <h1>UECADA</h1>
        <p>우리가 만든 SCADA 유익하다.</p>
      </div>

      <div class="control-board">
        <div class="board-header">
          <span>Ulsan Plant A</span>
          <strong>98.7%</strong>
        </div>

        <div class="status-grid">
          <article>
            <span>라인 가동률</span>
            <strong>96%</strong>
            <div class="meter"><i style="width: 96%"></i></div>
          </article>
          <article>
            <span>전력 부하</span>
            <strong>72%</strong>
            <div class="meter cyan"><i style="width: 72%"></i></div>
          </article>
          <article>
            <span>온도 안정성</span>
            <strong>Normal</strong>
            <div class="pulse-row">
              <i></i><i></i><i></i><i></i><i></i>
            </div>
          </article>
        </div>

        <div class="plant-map">
          <span class="node node-a"></span>
          <span class="node node-b"></span>
          <span class="node node-c"></span>
          <span class="path path-one"></span>
          <span class="path path-two"></span>
          <div class="machine machine-one"></div>
          <div class="machine machine-two"></div>
          <div class="machine machine-three"></div>
        </div>
      </div>
    </section>

    <section class="login-panel" aria-label="로그인">
      <div class="panel-inner">
        <div class="mobile-brand">
          <span class="brand-symbol">U</span>
          <span class="brand-copy">
            <strong>우리들의 스카다</strong>
            <small>유익하다</small>
          </span>
        </div>

        <div class="form-heading">
          <p>Welcome Back</p>
          <h2>로그인</h2>
        </div>

        <form class="login-form" @submit.prevent="submitLogin">
          <label>
            <span>아이디</span>
            <input v-model="loginForm.loginId" type="text" placeholder="아이디를 입력하세요" autocomplete="username" />
          </label>

          <label>
            <span>비밀번호</span>
            <input v-model="loginForm.password" type="password" placeholder="비밀번호를 입력하세요" autocomplete="current-password" />
          </label>

          <p v-if="loginError" class="login-auth-note login-auth-note--error">{{ loginError }}</p>

          <div class="form-options">
            <label class="check-row">
              <input type="checkbox" />
              <span>아이디 저장</span>
            </label>
            <a href="#">보안접속</a>
          </div>

          <button type="submit" class="login-button" :disabled="isSubmitting">
            {{ isSubmitting ? '로그인 중…' : '로그인' }}
          </button>
        </form>

        <nav class="account-links" aria-label="계정 메뉴">
          <button type="button" class="account-link-btn" @click="openModal('findId')">아이디 찾기</button>
          <button type="button" class="account-link-btn" @click="openModal('findPw')">비밀번호 찾기</button>
          <button type="button" class="account-link-btn" @click="openModal('signup')">회원가입</button>
        </nav>

        <p class="support-text">Copyright 2026 Hyundai Facility Control</p>
      </div>
    </section>

    <Teleport to="body">
      <div
        v-if="activeModal"
        class="login-auth-backdrop"
        role="presentation"
        @click.self="closeModal"
      >
        <article
          class="login-auth-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="login-auth-dialog-title"
        >
          <header class="login-auth-modal-head">
            <h2 id="login-auth-dialog-title">{{ activeModal ? modalTitle[activeModal] : '' }}</h2>
            <button type="button" class="login-auth-close" aria-label="닫기" @click="closeModal">
              <X :size="18" />
            </button>
          </header>

          <div v-if="activeModal === 'findId'" class="login-auth-body">
            <p class="login-auth-lead">가입 시 등록한 정보로 아이디를 찾습니다.</p>
            <form class="login-auth-form" @submit.prevent="submitFindId">
              <label>
                <span>이름</span>
                <input v-model="findIdForm.userName" type="text" name="find-name" autocomplete="name" placeholder="이름 입력" />
              </label>
              <label>
                <span>이메일</span>
                <input v-model="findIdForm.email" type="email" name="find-email" autocomplete="email" placeholder="이메일 입력" />
              </label>
              <label>
                <span>보안 답변</span>
                <input v-model="findIdForm.securityAnswer" type="text" placeholder="가입 시 선택한 질문의 답변" />
              </label>
              <button type="submit" class="login-auth-primary">아이디 찾기</button>
            </form>
          </div>

          <div v-else-if="activeModal === 'findPw'" class="login-auth-body">
            <p class="login-auth-lead">아이디 확인 후 보안 질문에 답하면 비밀번호를 재설정합니다.</p>
            <form class="login-auth-form" @submit.prevent="submitResetPassword">
              <label>
                <span>아이디</span>
                <input v-model="resetForm.loginId" type="text" name="pw-id" autocomplete="username" placeholder="아이디 입력" @blur="loadSecurityQuestion" />
              </label>
              <button type="button" class="login-auth-secondary" @click="loadSecurityQuestion">보안 질문 확인</button>
              <p v-if="securityQuestion" class="login-auth-question">{{ securityQuestion }}</p>
              <label>
                <span>보안 답변</span>
                <input v-model="resetForm.securityAnswer" type="text" placeholder="보안 답변 입력" />
              </label>
              <label>
                <span>새 비밀번호</span>
                <input v-model="resetForm.newPassword" type="password" autocomplete="new-password" placeholder="새 비밀번호" />
              </label>
              <label>
                <span>새 비밀번호 확인</span>
                <input v-model="resetForm.confirmPassword" type="password" autocomplete="new-password" placeholder="새 비밀번호 재입력" />
              </label>
              <button type="submit" class="login-auth-primary">비밀번호 변경</button>
            </form>
          </div>

          <div v-else class="login-auth-body">
            <p class="login-auth-lead">UECADA 관제 시스템 이용을 위한 계정을 만듭니다.</p>
            <form class="login-auth-form" @submit.prevent="submitSignup">
              <label>
                <span>사용자 ID</span>
                <input v-model="signupForm.userId" type="text" placeholder="예: U401" required />
              </label>
              <label>
                <span>아이디</span>
                <input v-model="signupForm.loginId" type="text" name="reg-id" autocomplete="username" placeholder="영문·숫자 조합" required />
              </label>
              <label>
                <span>비밀번호</span>
                <input v-model="signupForm.password" type="password" name="reg-pw" autocomplete="new-password" placeholder="8자 이상" required />
              </label>
              <label>
                <span>비밀번호 확인</span>
                <input v-model="signupForm.confirmPassword" type="password" name="reg-pw2" autocomplete="new-password" placeholder="비밀번호 재입력" required />
              </label>
              <label>
                <span>이름</span>
                <input v-model="signupForm.userName" type="text" name="reg-name" autocomplete="name" placeholder="이름" required />
              </label>
              <label>
                <span>이메일</span>
                <input v-model="signupForm.email" type="email" name="reg-email" autocomplete="email" placeholder="이메일" />
              </label>
              <label>
                <span>역할</span>
                <select v-model="signupForm.roleName" class="login-role-select">
                  <option value="OPERATOR">작업자</option>
                  <option value="MANAGER">라인 관리자</option>
                  <option value="ADMIN">관리자</option>
                </select>
              </label>
              <label>
                <span>담당 라인</span>
                <select v-model="signupForm.lineId" class="login-role-select">
                  <option value="">전체/관리자</option>
                  <option value="LINE-01">LINE-01</option>
                  <option value="LINE-02">LINE-02</option>
                  <option value="LINE-03">LINE-03</option>
                </select>
              </label>
              <label>
                <span>보안 질문</span>
                <input v-model="signupForm.securityQuestion" type="text" required />
              </label>
              <label>
                <span>보안 답변</span>
                <input v-model="signupForm.securityAnswer" type="text" required />
              </label>
              <button type="submit" class="login-auth-primary">가입 요청</button>
            </form>
          </div>

          <p v-if="modalMessage" class="login-auth-note">{{ modalMessage }}</p>
        </article>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
.login-role-select {
  width: 100%;
  margin-top: 6px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid #d0d7de;
  font: inherit;
  background: #fff;
  box-sizing: border-box;
}
.login-auth-secondary {
  justify-self: start;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #0f172a;
  font-weight: 800;
  cursor: pointer;
}
.login-auth-question {
  margin: 0;
  padding: 9px 10px;
  border-radius: 8px;
  background: #eef6ff;
  color: #0f4c81;
  font-weight: 800;
  font-size: 13px;
}
.login-auth-note--error {
  color: #b91c1c;
}
</style>
