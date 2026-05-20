<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ExternalLink, Factory, Loader2, RefreshCcw, X } from 'lucide-vue-next'
import { buildSmwpOverlayUrl } from '@/composables/useWebScadaLinks'

interface Props {
  open: boolean
  pageId?: string
  title?: string
  subtitle?: string
  size?: 'standard' | 'large' | 'fullscreen'
  minFrameWidth?: number
  minFrameHeight?: number
}

const props = withDefaults(defineProps<Props>(), {
  pageId: 'LDV',
  title: '웹스카다',
  subtitle: 'SMWP WebSCADA',
  size: 'fullscreen',
  minFrameWidth: 1366,
  minFrameHeight: 980,
})

const emit = defineEmits<{ (e: 'close'): void }>()

const dialogRef = ref<HTMLDialogElement | null>(null)
const iframeUrl = computed(() => buildSmwpOverlayUrl(props.pageId) ?? '')
const iframeLoaded = ref(false)
const loadTimedOut = ref(false)
const iframeReloadKey = ref(0)
const showLoading = computed(() => props.open && Boolean(iframeUrl.value) && !iframeLoaded.value && !loadTimedOut.value)
const dialogClasses = computed(() => ['scada-dialog', `scada-dialog--${props.size}`])
const dialogStyle = computed<Record<string, string>>(() => ({
  '--scada-frame-min-width': `${props.minFrameWidth}px`,
  '--scada-frame-min-height': `${props.minFrameHeight}px`,
}))
let loadTimer: number | null = null
let previouslyFocused: HTMLElement | null = null

function clearLoadTimer(): void {
  if (!loadTimer) return
  window.clearTimeout(loadTimer)
  loadTimer = null
}

function preconnect(url: string): void {
  try {
    const origin = new URL(url).origin
    const selector = `link[rel="preconnect"][href="${origin}"]`
    if (document.head.querySelector(selector)) return
    const link = document.createElement('link')
    link.rel = 'preconnect'
    link.href = origin
    link.crossOrigin = ''
    document.head.appendChild(link)
  } catch {
    // 설정값 검증은 buildSmwpOverlayUrl 에서 처리한다.
  }
}

function prepareIframeLoad(): void {
  clearLoadTimer()
  iframeLoaded.value = false
  loadTimedOut.value = false
  if (!iframeUrl.value) return
  preconnect(iframeUrl.value)
  loadTimer = window.setTimeout(() => {
    if (!iframeLoaded.value) loadTimedOut.value = true
  }, 8000)
}

function close(): void {
  emit('close')
}

function onDialogClick(event: MouseEvent): void {
  if (event.target === dialogRef.value) close()
}

function onDialogClose(): void {
  if (props.open) close()
}

function openInNewTab(): void {
  if (!iframeUrl.value) return
  window.open(iframeUrl.value, '_blank', 'noopener,noreferrer')
}

function reloadIframe(): void {
  iframeReloadKey.value += 1
  prepareIframeLoad()
}

function onIframeLoad(): void {
  iframeLoaded.value = true
  loadTimedOut.value = false
  clearLoadTimer()
}

watch(
  () => [props.open, props.pageId] as const,
  ([isOpen], [wasOpen]) => {
    const dlg = dialogRef.value
    if (!dlg) return
    if (isOpen) {
      previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
      if (!dlg.open) dlg.showModal()
      prepareIframeLoad()
    } else if (dlg.open) {
      dlg.close()
      clearLoadTimer()
      iframeLoaded.value = false
      loadTimedOut.value = false
      if (wasOpen) previouslyFocused?.focus()
    }
  },
)

onMounted(() => {
  const dlg = dialogRef.value
  if (!props.open || !dlg) return
  previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
  if (!dlg.open) dlg.showModal()
  prepareIframeLoad()
})

onBeforeUnmount(() => {
  clearLoadTimer()
  const dlg = dialogRef.value
  if (dlg?.open) dlg.close()
})
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialogRef"
      :class="dialogClasses"
      :style="dialogStyle"
      :aria-label="title"
      @click="onDialogClick"
      @close="onDialogClose"
      @cancel.prevent="close"
    >
      <header class="scada-dialog-head">
        <div class="scada-dialog-head-left">
          <span class="scada-dialog-icon" aria-hidden="true"><Factory :size="18" /></span>
          <div class="scada-dialog-titles">
            <p>{{ subtitle }}</p>
            <h2>{{ title }}</h2>
          </div>
          <span class="scada-dialog-pill">#{{ pageId }}</span>
        </div>
        <div class="scada-dialog-actions">
          <button
            type="button"
            class="scada-dialog-iconbtn"
            title="새 창에서 열기"
            aria-label="새 창에서 열기"
            :disabled="!iframeUrl"
            @click="openInNewTab"
          >
            <ExternalLink :size="16" />
          </button>
          <button
            type="button"
            class="scada-dialog-iconbtn scada-dialog-iconbtn--close"
            title="닫기"
            aria-label="닫기"
            @click="close"
          >
            <X :size="18" />
          </button>
        </div>
      </header>

      <div class="scada-dialog-body">
        <iframe
          v-if="open && iframeUrl"
          :key="`${pageId}-${iframeReloadKey}`"
          class="scada-dialog-iframe"
          :src="iframeUrl"
          title="웹스카다 (SMWP)"
          referrerpolicy="no-referrer"
          scrolling="yes"
          allow="fullscreen"
          @load="onIframeLoad"
        />
        <div v-if="showLoading" class="scada-dialog-loading" aria-live="polite">
          <Loader2 :size="28" class="scada-dialog-spin" />
          <p>SMWP 화면을 불러오는 중...</p>
        </div>
        <div v-else-if="loadTimedOut" class="scada-dialog-fallback" role="status">
          <Factory :size="34" />
          <h3>SMWP 응답 확인 중</h3>
          <p>화면이 계속 비어 있으면 새 창에서 열거나 다시 시도해 주세요.</p>
          <div class="scada-dialog-fallback-actions">
            <button type="button" class="scada-dialog-action" @click="reloadIframe">
              <RefreshCcw :size="16" />
              <span>다시 시도</span>
            </button>
            <button type="button" class="scada-dialog-action" @click="openInNewTab">
              <ExternalLink :size="16" />
              <span>새 창에서 열기</span>
            </button>
          </div>
        </div>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.scada-dialog {
  width: min(1280px, calc(100vw - 32px));
  height: min(820px, calc(100vh - 32px));
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  padding: 0;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 14px;
  box-shadow: 0 24px 60px rgba(2, 6, 23, 0.45);
  background: #0f172a;
  color: #e2e8f0;
  overflow: hidden;
}

.scada-dialog--large {
  width: min(1400px, calc(100vw - 80px));
  height: min(900px, calc(100vh - 72px));
}

.scada-dialog--fullscreen {
  width: calc(100vw - 16px);
  height: calc(100vh - 16px);
  border-radius: 10px;
}

.scada-dialog[open] { display: flex; flex-direction: column; }
.scada-dialog::backdrop { background: rgba(7, 14, 30, 0.62); backdrop-filter: blur(6px); }
.scada-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: 0 0 auto;
  padding: 10px 14px;
  background: #111827;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}
.scada-dialog-head-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.scada-dialog-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(56, 189, 248, 0.14);
  color: #38bdf8;
}
.scada-dialog-titles p {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #94a3b8;
}
.scada-dialog-titles h2 { margin: 2px 0 0; font-size: 16px; color: #f8fafc; }
.scada-dialog-pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
  font-size: 12px;
  font-weight: 700;
}
.scada-dialog-actions { display: flex; gap: 6px; }
.scada-dialog-iconbtn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
}
.scada-dialog-iconbtn:hover:not(:disabled) { background: rgba(56, 189, 248, 0.16); color: #e0f2fe; }
.scada-dialog-iconbtn:disabled { opacity: 0.5; cursor: not-allowed; }
.scada-dialog-iconbtn--close:hover:not(:disabled) { background: rgba(239, 68, 68, 0.18); color: #fecaca; }
.scada-dialog-body {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #fff;
  overflow: auto;
}
.scada-dialog-iframe {
  display: block;
  width: 100%;
  height: 100%;
  min-width: var(--scada-frame-min-width, 1366px);
  min-height: var(--scada-frame-min-height, 980px);
  border: 0;
  background: #fff;
}
.scada-dialog-loading,
.scada-dialog-fallback {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  color: #cbd5e1;
  background: rgba(2, 6, 23, 0.9);
}
.scada-dialog-spin { animation: scada-dialog-spin 1s linear infinite; color: #38bdf8; }
@keyframes scada-dialog-spin { to { transform: rotate(360deg); } }
.scada-dialog-loading p,
.scada-dialog-fallback p { margin: 0; font-size: 13px; color: #94a3b8; }
.scada-dialog-fallback svg { color: #38bdf8; }
.scada-dialog-fallback h3 { margin: 0; font-size: 16px; color: #f8fafc; }
.scada-dialog-fallback-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 8px; }
.scada-dialog-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid rgba(56, 189, 248, 0.38);
  border-radius: 8px;
  background: rgba(56, 189, 248, 0.16);
  color: #e0f2fe;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}
.scada-dialog-action:hover { background: rgba(56, 189, 248, 0.26); }
@media (max-width: 720px) {
  .scada-dialog { width: 100vw; height: 100vh; border-radius: 0; }
}
</style>
