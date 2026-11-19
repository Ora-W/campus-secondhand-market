<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import api from './api.js'
import store from './store.js'

import Home from './views/Home.vue'
import ProductDetail from './views/ProductDetail.vue'
import Login from './views/Login.vue'
import Register from './views/Register.vue'
import UserCenter from './views/UserCenter.vue'
import MerchantCenter from './views/MerchantCenter.vue'
import AdminCenter from './views/AdminCenter.vue'

const view = ref('home')      // 当前视图
const detailProductId = ref(null)
const notice = ref('')
const noticeType = ref('success')   // success / error / info

// 全局消息角标：登录后每 15 秒轮询，无需点进页面即可看到新消息
const summary = ref(null)
let summaryTimer = null

async function loadSummary(silent = true) {
  if (!store.isLoggedIn()) { summary.value = null; return }
  const k = noticeKeys()
  const seenNegoId = Number(localStorage.getItem(k.seen) || 0)
  const seenReturnId = Number(localStorage.getItem(k.ret) || 0)
  try {
    const d = (await api.get('/notifications/summary', { params: { seenNegoId, seenReturnId } })).data.data
    const prev = summary.value
    summary.value = d
    // 注销申请通过后自动退出登录
    if (d.cancelStatus === 'APPROVED') {
      summary.value = null
      store.clearAuth()
      showNotice('账号注销申请已通过，账号已停用', 'info', 6000)
      return
    }
    const lastToast = Number(localStorage.getItem(k.toast) || 0)
    if (d.latestId && d.latestId > lastToast) {
      localStorage.setItem(k.toast, d.latestId)
      if (silent && prev) showNotice('🔔 您有新的议价/留言消息，请及时查看', 'info', 5000)
    }
    if (silent && prev && (d.total || 0) > (prev.total || 0))
      showNotice('🔔 ' + (d.hint || '您有新的待处理事项'), 'info', 5000)
  } catch (e) { /* 轮询失败静默处理 */ }
}

function noticeKeys() {
  const base = `_${store.user?.id}_${store.user?.role}`
  return { seen: 'notifySeen' + base, ret: 'notifySeenRet' + base, toast: 'notifyToast' + base }
}
/** 进入个人/商家/管理后台后，清除已读角标 */
function markCenterRead() {
  if (!summary.value) return
  const k = noticeKeys()
  if (summary.value.latestId) localStorage.setItem(k.seen, summary.value.latestId)
  if (summary.value.latestReturnId) localStorage.setItem(k.ret, summary.value.latestReturnId)
}

const homeCenter = computed(() => store.isAdmin() ? 'admin' : (store.isMerchant() ? 'merchant' : 'user'))
function badgeOf(key) {
  if (!summary.value || !store.isLoggedIn()) return 0
  return (key === 'user' || key === 'merchant' || key === 'admin') ? (summary.value.total || 0) : 0
}

function goto(v, extra = null) {
  view.value = v
  detailProductId.value = extra
  notice.value = ''
  window.scrollTo(0, 0)
  // 登录后立即拉取一次消息角标；进入对应中心时清除已读
  if (store.isLoggedIn()) {
    if (v === homeCenter.value) markCenterRead()
    if (!summaryTimer) startSummaryPolling()
    else loadSummary(false)
  }
}

function startSummaryPolling() {
  loadSummary(false)
  if (summaryTimer) clearInterval(summaryTimer)
  summaryTimer = setInterval(() => loadSummary(true), 15000)
}

/**
 * 统一提示：showNotice('成功信息') / showNotice('错误信息', 'error')
 * 兼容旧用法 showNotice('信息', 5000)
 */
function showNotice(msg, type = 'success', dur = 3000) {
  if (typeof type === 'number') { dur = type; type = 'success' }
  noticeType.value = type
  notice.value = msg
  clearTimeout(window.__noticeTimer)
  window.__noticeTimer = setTimeout(() => notice.value = '', dur)
}

async function logout() {
  store.clearAuth()
  goto('home')
  showNotice('已退出登录')
}

const navTabs = computed(() => {
  const list = [{ key: 'home', label: '🏠 首页' }]
  if (!store.isLoggedIn()) {
    list.push({ key: 'login', label: '登录' })
    list.push({ key: 'register', label: '注册' })
  } else {
    list.push({ key: 'user', label: '👤 个人中心' })
    if (store.isMerchant()) list.push({ key: 'merchant', label: '🏪 商家中心' })
    if (store.isAdmin()) list.push({ key: 'admin', label: '🛡️ 管理后台' })
  }
  return list
})

onMounted(() => {
  // 如果有 token 验证一下有效性
  if (store.isLoggedIn()) {
    api.get('/users/me').then(r => {
      store.user = r.data.data
    }).catch(() => {
      store.clearAuth()
    })
    startSummaryPolling()
  }
})

onUnmounted(() => { if (summaryTimer) clearInterval(summaryTimer) })

// 暴露给子组件
defineExpose({ goto, showNotice })
</script>

<template>
<div class="shell">
  <header>
    <div class="brand" @click="goto('home')">
      <h1>🎒 校园二手交易市场</h1>
      <p>让闲置好物在校园里流转</p>
    </div>
    <div class="header-right">
      <template v-if="store.isLoggedIn()">
        <span class="user-tag">
          {{ store.user?.username || '' }}
          <span v-if="store.isAdmin()" class="role admin">管理员</span>
          <span v-else-if="store.isMerchant()" class="role merchant">商家</span>
          <span v-else class="role user">用户</span>
          <span class="bal">💰 ￥{{ store.user?.walletBalance?.toFixed(2) || '0.00' }}</span>
          <span class="bal">⭐ {{ store.user?.points || 0 }}</span>
        </span>
        <button class="btn-ghost logout-btn" @click="logout">退出登录</button>
      </template>
      <template v-else>
        <button class="btn-ghost" @click="goto('login')">登录</button>
        <button class="btn-primary" @click="goto('register')">注册</button>
      </template>
    </div>
  </header>

  <nav class="top-nav">
    <button
      v-for="t in navTabs"
      :key="t.key"
      :class="{ active: view === t.key || (t.key==='home' && view==='detail') }"
      @click="goto(t.key)"
    >
      {{ t.label }}
      <span v-if="badgeOf(t.key)" class="nav-badge">{{ badgeOf(t.key) }}</span>
    </button>
  </nav>

  <!-- 明显提示：封禁 / 店铺关闭 / 待处理消息 -->
  <div v-if="store.isLoggedIn() && summary && (summary.banned || summary.shopClosed || summary.total > 0)"
       class="alert-bar" :class="{ danger: summary.banned || summary.shopClosed }">
    <span v-if="summary.banned">
      🚫 当前账号已被封禁<template v-if="summary.banUntil">至 {{ new Date(summary.banUntil).toLocaleString() }}</template>，原因：{{ summary.banReason || '违规' }}
    </span>
    <span v-else-if="summary.shopClosed">🏪 店铺已被平台暂时关闭，暂时无法发布/编辑商品，请联系管理员</span>
    <span v-else>🔔 {{ summary.hint }}，点击查看处理</span>
    <button v-if="!summary.banned" class="alert-go" @click="goto(homeCenter)">立即处理 →</button>
  </div>

  <div v-if="notice" class="notice" :class="noticeType">{{ notice }}</div>

  <main>
    <Home v-if="view==='home'" :on-goto="goto" :on-notice="showNotice" />
    <ProductDetail v-else-if="view==='detail'" :product-id="detailProductId" :on-goto="goto" :on-notice="showNotice" />
    <Login v-else-if="view==='login'" :on-goto="goto" :on-notice="showNotice" />
    <Register v-else-if="view==='register'" :on-goto="goto" :on-notice="showNotice" />
    <UserCenter v-else-if="view==='user'" :on-goto="goto" :on-notice="showNotice" />
    <MerchantCenter v-else-if="view==='merchant'" :on-goto="goto" :on-notice="showNotice" />
    <AdminCenter v-else-if="view==='admin'" :on-goto="goto" :on-notice="showNotice" />
  </main>

  <footer>校园二手交易市场 · Spring Boot 3 + MyBatis-Plus + Vue 3</footer>
</div>
</template>

<style scoped>
.nav-badge {
  display: inline-block;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  margin-left: 5px;
  border-radius: 99px;
  background: #9c3730;
  color: white;
  font-size: 11px;
  font-weight: 600;
  vertical-align: middle;
}
.alert-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 16px;
  padding: 12px 18px;
  border-radius: 10px;
  background: #eef3f8;
  color: #2d4a66;
  font-size: 14px;
}
.alert-bar.danger { background: #fbeceb; color: #94322c; }
.alert-go {
  flex-shrink: 0;
  background: #35597f;
  color: white;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
}
.alert-bar.danger .alert-go { background: #c0392b; }
.logout-btn { background:#f2f4f7; color:#4b5563; }
.logout-btn:hover { background:#e8ebef; }

</style>
