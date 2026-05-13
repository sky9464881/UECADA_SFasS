<script setup>
const goDashboard = () => {
  window.location.hash = '#/dashboard'
}
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
            <input type="text" placeholder="아이디를 입력하세요" />
          </label>

          <label>
            <span>비밀번호</span>
            <input type="password" placeholder="비밀번호를 입력하세요" />
          </label>

          <div class="form-options">
            <label class="check-row">
              <input type="checkbox" />
              <span>아이디 저장</span>
            </label>
            <a href="#">보안접속</a>
          </div>

          <button type="button" class="login-button" @click="goDashboard">로그인</button>
        </form>

        <nav class="account-links" aria-label="계정 메뉴">
          <a href="#">아이디 찾기</a>
          <a href="#">비밀번호 찾기</a>
          <a href="#">회원가입</a>
        </nav>

        <p class="support-text">Copyright 2026 Hyundai Facility Control</p>
      </div>
    </section>
  </main>
</template>
