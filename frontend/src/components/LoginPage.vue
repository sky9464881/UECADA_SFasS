<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { X } from 'lucide-vue-next'
import { login } from '../api/auth.js'

const loginId = ref('')
const password = ref('')
const loginError = ref('')
const loginLoading = ref(false)

const goDashboard = async () => {
  if (!loginId.value || !password.value) {
    loginError.value = '아이디와 비밀번호를 입력하세요.'
    return
  }
  loginLoading.value = true
  loginError.value = ''
  try {
    const user = await login(loginId.value, password.value)
    sessionStorage.setItem('user', JSON.stringify(user))
    window.location.hash = '#/dashboard'
  } catch {
    loginError.value = '아이디 또는 비밀번호가 올바르지 않습니다.'
  } finally {
    loginLoading.value = false
  }
}

const activeModal = ref(null)

const modalTitle = {
  findId: '아이디 찾기',
  findPw: '비밀번호 찾기',
  signup: '회원가입',
}

const openModal = (id) => {
  activeModal.value = id
}

const closeModal = () => {
  activeModal.value = null
}

const onKeydown = (e) => {
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

        <form class="login-form">
          <label>
            <span>아이디</span>
            <input v-model="loginId" type="text" placeholder="아이디를 입력하세요" autocomplete="username" />
          </label>

          <label>
            <span>비밀번호</span>
            <input v-model="password" type="password" placeholder="비밀번호를 입력하세요" autocomplete="current-password" @keyup.enter="goDashboard" />
          </label>

          <div class="form-options">
            <label class="check-row">
              <input type="checkbox" />
              <span>아이디 저장</span>
            </label>
            <a href="#">보안접속</a>
          </div>

          <p v-if="loginError" style="color:#d92d20;font-size:13px;margin:-4px 0 4px;">{{ loginError }}</p>
          <button type="button" class="login-button" :disabled="loginLoading" @click="goDashboard">
            {{ loginLoading ? '로그인 중...' : '로그인' }}
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
            <h2 id="login-auth-dialog-title">{{ modalTitle[activeModal] }}</h2>
            <button type="button" class="login-auth-close" aria-label="닫기" @click="closeModal">
              <X :size="18" />
            </button>
          </header>

          <div v-if="activeModal === 'findId'" class="login-auth-body">
            <p class="login-auth-lead">가입 시 등록한 정보로 아이디를 찾습니다.</p>
            <form class="login-auth-form" @submit.prevent>
              <label>
                <span>이름</span>
                <input type="text" name="find-name" autocomplete="name" placeholder="이름 입력" />
              </label>
              <label>
                <span>이메일</span>
                <input type="email" name="find-email" autocomplete="email" placeholder="이메일 입력" />
              </label>
              <button type="button" class="login-auth-primary">인증 메일 발송</button>
            </form>
          </div>

          <div v-else-if="activeModal === 'findPw'" class="login-auth-body">
            <p class="login-auth-lead">아이디 확인 후 비밀번호 재설정 링크를 보냅니다.</p>
            <form class="login-auth-form" @submit.prevent>
              <label>
                <span>아이디</span>
                <input type="text" name="pw-id" autocomplete="username" placeholder="아이디 입력" />
              </label>
              <label>
                <span>이메일</span>
                <input type="email" name="pw-email" autocomplete="email" placeholder="가입 이메일 입력" />
              </label>
              <button type="button" class="login-auth-primary">재설정 링크 발송</button>
            </form>
          </div>

          <div v-else class="login-auth-body">
            <p class="login-auth-lead">UECADA 관제 시스템 이용을 위한 계정을 만듭니다.</p>
            <form class="login-auth-form" @submit.prevent>
              <label>
                <span>아이디</span>
                <input type="text" name="reg-id" autocomplete="username" placeholder="영문·숫자 조합" />
              </label>
              <label>
                <span>비밀번호</span>
                <input type="password" name="reg-pw" autocomplete="new-password" placeholder="8자 이상" />
              </label>
              <label>
                <span>비밀번호 확인</span>
                <input type="password" name="reg-pw2" autocomplete="new-password" placeholder="비밀번호 재입력" />
              </label>
              <label>
                <span>이름</span>
                <input type="text" name="reg-name" autocomplete="name" placeholder="이름" />
              </label>
              <label>
                <span>이메일</span>
                <input type="email" name="reg-email" autocomplete="email" placeholder="이메일" />
              </label>
              <button type="button" class="login-auth-primary">가입 요청</button>
            </form>
          </div>

          <p class="login-auth-note">데모 화면입니다. 실제 인증·가입은 연동 후 사용합니다.</p>
        </article>
      </div>
    </Teleport>
  </main>
</template>
