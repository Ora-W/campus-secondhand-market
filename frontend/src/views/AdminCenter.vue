<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '../api.js'

const props = defineProps(['onGoto', 'onNotice'])
const notice = props.onNotice

const tab = ref('dashboard')   // dashboard / users / products / orders / blacklist / levels
const dashboard = ref({})
const keyword = ref('')
const users = ref([])
const pendingUsers = ref([])
const pendingProducts = ref([])
const allOrders = ref([])
const levels = ref([])
const blacklists = ref([])
const merchantStats = ref([])
const userDetail = ref(null)
const detailOpen = ref(false)
const carouselList = ref([])
const blacklistRows = ref([])
const cancellations = ref([])
const pendingCancelCount = computed(() => cancellations.value.filter(c => c.request.status === 'PENDING').length)
const blacklistForm = ref({ buyerId: '', reason: '' })
const banOpen = ref(false)
const banTarget = ref(null)
const banForm = ref({ days: 7, reason: '' })

function switchTab(t) { tab.value = t; refresh() }

async function refresh() {
  // 每个请求独立容错：某个表未创建时不会拖垮整个后台数据
  const [dash, pu, pp, ord, bl, ca, usr, lv, ms, cp] = await Promise.allSettled([
    api.get('/admin/dashboard'),
    api.get('/admin/users/pending'),
    api.get('/admin/products/pending'),
    api.get('/admin/orders'),
    api.get('/admin/blacklists'),
    api.get('/admin/cancellations'),
    api.get('/admin/users', { params: { keyword: keyword.value || null } }),
    api.get('/admin/merchant-levels'),
    api.get('/admin/merchants'),
    api.get('/admin/products'),
  ])
  const val = r => r.status === 'fulfilled' ? (r.value.data.data || []) : null
  if (val(dash)) dashboard.value = val(dash)
  if (val(pu)) pendingUsers.value = val(pu)
  if (val(pp)) pendingProducts.value = val(pp)
  if (val(ord)) allOrders.value = val(ord)
  if (val(bl)) blacklistRows.value = val(bl)
  if (val(ca)) cancellations.value = val(ca)
  if (val(usr)) users.value = val(usr)
  if (val(lv)) levels.value = val(lv)
  if (val(ms)) merchantStats.value = val(ms)
  if (val(cp)) carouselList.value = val(cp)
}

/** 定时刷新待审核/订单/黑名单数量，无需点击页签 */
let pollTimer = null
async function refreshCounts() {
  const [dash, pu, pp, ord, bl, ca, usr] = await Promise.allSettled([
    api.get('/admin/dashboard'),
    api.get('/admin/users/pending'),
    api.get('/admin/products/pending'),
    api.get('/admin/orders'),
    api.get('/admin/blacklists'),
    api.get('/admin/cancellations'),
    api.get('/admin/users'),
  ])
  const val = r => r.status === 'fulfilled' ? (r.value.data.data || []) : null
  if (val(dash)) dashboard.value = val(dash)
  if (val(pu)) pendingUsers.value = val(pu)
  if (val(pp)) pendingProducts.value = val(pp)
  if (val(ord)) allOrders.value = val(ord)
  if (val(bl)) blacklistRows.value = val(bl)
  if (val(ca)) cancellations.value = val(ca)
  if (val(usr) && tab.value === 'users') users.value = val(usr)
}

// ---------- 轮播推荐 / 强制下架 ----------
async function toggleCarousel(id, on) {
  try { await api.post(`/admin/products/${id}/carousel`, null, { params: { on } }); notice(on ? '已设为首页轮播 ✅' : '已取消轮播'); refresh() }
  catch(e) { notice(e.message, 'error') }
}
async function adminTakeDown(id) {
  const reason = prompt('强制下架原因:') || ''
  if (!confirm('确定强制下架该商品？')) return
  try { await api.post(`/admin/products/${id}/take-down`, null, { params: { reason } }); notice('已强制下架'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

// ---------- 店铺关闭 / 恢复 ----------
async function closeShop(id) {
  const reason = prompt('关闭店铺原因（全部商品将下架）:') || '违规经营'
  if (!confirm('确定暂时关闭该店铺？所有在售/待审核商品会被下架')) return
  try { await api.post(`/admin/merchants/${id}/close-shop`, null, { params: { reason } }); notice('店铺已关闭'); refresh() }
  catch(e) { notice(e.message, 'error') }
}
async function openShop(id) {
  if (!confirm('恢复该店铺营业？')) return
  try { await api.post(`/admin/merchants/${id}/open-shop`); notice('店铺已恢复'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

// ---------- 封禁弹窗 ----------
function openBan(u) {
  banTarget.value = u
  banForm.value = { days: 7, reason: '' }
  banOpen.value = true
}
async function submitBan() {
  const u = banTarget.value
  if (!u) return
  let until = null
  if (banForm.value.days > 0) {
    const d = new Date(Date.now() + banForm.value.days * 86400000)
    const p = n => String(n).padStart(2, '0')
    until = `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }
  try {
    await api.post(`/admin/users/${u.id}/ban`, null, { params: { until, reason: banForm.value.reason || '' } })
    notice(banForm.value.days > 0 ? `已封禁 ${banForm.value.days} 天` : '已永久封禁')
    banOpen.value = false
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// ---------- 注销审核 ----------
async function reviewCancel(id, approved) {
  const note = approved ? '' : (prompt('拒绝原因（可选）:') || '')
  if (!confirm(approved ? '通过后该账号将被停用、无法登录，确定通过？' : '确定拒绝该注销申请？')) return
  try {
    await api.post(`/admin/cancellations/${id}/review`, null, { params: { approved, note } })
    notice(approved ? '已通过注销申请，账号已停用' : '已拒绝注销申请')
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// ---------- 用户完整注册资料 ----------
async function openUserDetail(id) {
  try {
    userDetail.value = (await api.get(`/admin/users/${id}`)).data.data
    detailOpen.value = true
  } catch(e) { notice(e.message, 'error') }
}
function closeDetail() { detailOpen.value = false; userDetail.value = null }

// ---------- 用户操作 ----------
async function auditUser(id, approved) {
  if (!confirm(approved ? '通过此用户的注册审核？' : '拒绝此用户的注册审核？')) return
  try {
    await api.post(`/admin/users/${id}/audit`, null, { params: { approved } })
    notice(approved ? '已通过 ✅' : '已拒绝')
    refresh()
  } catch(e) { notice(e.message, 'error') }
}
async function recharge(id) {
  const amount = prompt('充值金额（元）:')
  if (!amount) return
  try { await api.post(`/admin/users/${id}/recharge`, null, { params: { amount } }); notice('充值成功 ✅'); refresh() }
  catch(e) { notice(e.message, 'error') }
}
async function unbanUser(id) {
  if (!confirm('解封此用户？')) return
  try { await api.post(`/admin/users/${id}/unban`); notice('已解封'); refresh() }
  catch(e) { notice(e.message, 'error') }
}
async function deleteUser(id) {
  if (!confirm('确定删除此用户？不可恢复')) return
  try { await api.delete(`/admin/users/${id}`); notice('已删除'); refresh() }
  catch(e) { notice(e.message, 'error') }
}
async function setLevel(merchantId) {
  const level = prompt('商家等级 (1-5):')
  if (!level) return
  try { await api.post(`/admin/merchants/${merchantId}/level`, null, { params: { level } }); notice('已设置'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

// ---------- 商品审核 ----------
async function auditProduct(id, approved) {
  const reason = approved ? '' : (prompt('驳回原因:') || '')
  try {
    await api.post(`/admin/products/${id}/audit`, null, { params: { approved, reason } })
    notice(approved ? '已上架 ✅' : '已驳回')
    refresh()
  } catch(e) { notice(e.message, 'error') }
}
// ---------- 黑名单 ----------
async function addBlacklist() {
  const buyerId = Number(blacklistForm.value.buyerId)
  if (!buyerId) return notice('请选择要拉黑的买家', 'error')
  try {
    await api.post('/admin/blacklists', { buyerId, reason: blacklistForm.value.reason || '恶意购买', scope: 'SITE' })
    notice('已加入平台黑名单')
    blacklistForm.value = { buyerId: '', reason: '' }
    refresh()
  } catch(e) { notice(e.message, 'error') }
}
async function removeBlacklist(id) {
  if (!confirm('移除此黑名单？')) return
  try { await api.delete(`/admin/blacklists/${id}`); notice('已移除'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

onMounted(() => {
  refresh()
  pollTimer = setInterval(refreshCounts, 15000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
<section>
  <h2>🛡️ 管理员后台</h2>
  <nav class="sub-tabs">
    <button :class="{active: tab==='dashboard'}" @click="switchTab('dashboard')">📊 仪表盘</button>
    <button :class="{active: tab==='users'}" @click="switchTab('users')">👥 用户管理 ({{ users.length }})</button>
    <button :class="{active: tab==='products'}" @click="switchTab('products')">🛍️ 商品审核 ({{ pendingProducts.length }})</button>
    <button :class="{active: tab==='carousel'}" @click="switchTab('carousel')">🖼️ 轮播推荐</button>
    <button :class="{active: tab==='orders'}" @click="switchTab('orders')">📦 订单 ({{ allOrders.length }})</button>
    <button :class="{active: tab==='blacklist'}" @click="switchTab('blacklist')">🚫 黑名单 ({{ blacklistRows.length }})</button>
    <button :class="{active: tab==='cancel'}" @click="switchTab('cancel')">🗑️ 注销审核 ({{ pendingCancelCount }})</button>
    <button :class="{active: tab==='merchants'}" @click="switchTab('merchants')">⭐ 商家评价</button>
    <button :class="{active: tab==='levels'}" @click="switchTab('levels')">💎 商家等级</button>
  </nav>

  <!-- 仪表盘 -->
  <div v-if="tab==='dashboard'" class="panel">
    <div class="stat-row">
      <div class="stat"><div class="n">{{ dashboard.userCount }}</div><div>总用户数</div></div>
      <div class="stat"><div class="n">{{ dashboard.merchantCount }}</div><div>商家数</div></div>
      <div class="stat"><div class="n yellow">{{ dashboard.pendingAuditCount }}</div><div>待审核用户</div></div>
      <div class="stat"><div class="n yellow">{{ dashboard.pendingProductCount }}</div><div>待审核商品</div></div>
      <div class="stat"><div class="n">{{ dashboard.orderCount }}</div><div>总订单数</div></div>
      <div class="stat"><div class="n green">￥{{ (dashboard.platformFundSum || 0).toFixed(2) }}</div><div>平台暂存总额</div></div>
    </div>
    <div class="fund-box">
      <div class="fund-title">💰 平台资金（买家确认收货前，货款由平台暂存，不进入商家钱包）</div>
      <div class="stat-row">
        <div class="stat"><div class="n green">￥{{ (dashboard.platformFundSum || 0).toFixed(2) }}</div><div>暂存中 · {{ dashboard.platformFundCount || 0 }} 单待确认收货</div></div>
        <div class="stat"><div class="n">￥{{ (dashboard.platformSettledSum || 0).toFixed(2) }}</div><div>已结算给商家</div></div>
        <div class="stat"><div class="n yellow">￥{{ (dashboard.platformFeeSum || 0).toFixed(2) }}</div><div>平台已收手续费</div></div>
        <div class="stat"><div class="n">￥{{ (dashboard.platformRefundedSum || 0).toFixed(2) }}</div><div>已退款</div></div>
      </div>
    </div>
  </div>

  <!-- 用户管理 -->
  <div v-else-if="tab==='users'" class="panel">
    <div class="toolbar">
      <input v-model="keyword" @keyup.enter="refresh" placeholder="搜索用户名/姓名/手机号">
      <button @click="refresh">搜索</button>
    </div>
    <div v-if="pendingUsers.length" class="pending-block">
      <h4>待审核注册（{{ pendingUsers.length }}）</h4>
      <div v-for="u in pendingUsers" :key="'p'+u.id" class="pending-row">
        <span>#{{ u.id }} <b>{{ u.username }}</b> · {{ u.realName || '未填姓名' }} · {{ u.role }}</span>
        <span class="actions">
          <button class="sm" @click="openUserDetail(u.id)">📋 资料</button>
          <button class="sm ok" @click="auditUser(u.id,true)">✅ 通过</button>
          <button class="sm danger" @click="auditUser(u.id,false)">❌ 拒绝</button>
        </span>
      </div>
    </div>
    <table class="tbl">
      <thead>
        <tr><th>ID</th><th>用户名</th><th>角色</th><th>姓名</th><th>店铺</th><th>审核</th><th>钱包</th><th>等级</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td>{{ u.id }}</td>
          <td>{{ u.username }}</td>
          <td><span class="tag">{{ u.role }}</span></td>
          <td>{{ u.realName || '—' }}</td>
          <td>{{ u.shopName || '—' }}</td>
          <td><span class="audit" :class="u.auditStatus">{{ u.auditStatus }}</span>
            <span v-if="u.banUntil" class="audit REJECTED" style="margin-left:4px">封禁至 {{ new Date(u.banUntil).toLocaleDateString() }}</span>
          </td>
          <td>￥{{ u.walletBalance?.toFixed(2) || '0' }}</td>
          <td>{{ u.merchantLevel || '-' }}</td>
          <td class="actions">
            <button class="sm" @click="openUserDetail(u.id)">📋</button>
            <button class="sm" v-if="u.auditStatus==='PENDING'" @click="auditUser(u.id,true)">✅</button>
            <button class="sm" v-if="u.auditStatus==='PENDING'" @click="auditUser(u.id,false)">❌</button>
            <button class="sm ok" v-if="u.auditStatus==='REJECTED'" @click="auditUser(u.id,true)" title="重新通过审核">✅ 重新通过</button>
            <button class="sm" v-if="u.auditStatus==='APPROVED'" @click="recharge(u.id)">💰</button>
            <button class="sm" v-if="u.role==='MERCHANT'" @click="setLevel(u.id)">💎</button>
            <button class="sm warn" v-if="!u.banUntil" @click="openBan(u)">🚫 封禁</button>
            <button class="sm ok" v-if="u.banUntil" @click="unbanUser(u.id)">🔓 解封</button>
            <button class="sm danger" v-if="u.role!=='ADMIN'" @click="deleteUser(u.id)">🗑️</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- 商品审核 -->
  <div v-else-if="tab==='products'" class="panel">
    <div v-if="!pendingProducts.length" class="empty">暂无待审核商品 ✅</div>
    <div v-for="item in pendingProducts" :key="item.product.id" class="prod-row">
      <div class="thumb" :style="{ backgroundImage: item.coverImage ? 'url('+item.coverImage+')' : 'none', backgroundColor: item.coverImage ? 'transparent' : '#eef0f3' }">
        <span v-if="!item.coverImage" class="noimg">暂无图片</span>
      </div>
      <div class="info">
        <h4>{{ item.product.name }} <span class="tag">{{ item.product.category }}</span></h4>
        <p class="sub">￥{{ item.product.price }} · 库存 {{ item.product.stock }} · 新旧 {{ item.product.conditionLevel }}</p>
        <p class="sub">卖家 ID {{ item.product.sellerId }} · 店铺 {{ item.product.shopName }}</p>
        <p class="desc">{{ item.product.description || '（无描述）' }}</p>
        <div class="actions">
          <button class="btn-danger" @click="auditProduct(item.product.id, false)">❌ 驳回</button>
          <button class="btn-primary" @click="auditProduct(item.product.id, true)">✅ 通过上架</button>
        </div>
      </div>
    </div>
  </div>

  <!-- 轮播推荐 -->
  <div v-else-if="tab==='carousel'" class="panel">
    <p class="sub" style="margin-top:0">勾选后商品会出现在商城首页轮播位；轮播位不足 4 件时会自动用最新在售商品补齐。</p>
    <div class="carousel-grid">
      <div v-for="item in carouselList" :key="item.product.id" class="c-card">
        <div class="thumb" :style="{ backgroundImage: item.coverImage ? 'url('+item.coverImage+')' : 'none', backgroundColor: item.coverImage ? 'transparent' : '#eef0f3' }">
          <span v-if="!item.coverImage" class="noimg">暂无图片</span>
          <span class="tag">{{ item.product.status }}</span>
        </div>
        <div class="c-info">
          <h4>{{ item.product.name }}</h4>
          <p class="sub">￥{{ item.product.price }} · 店铺 {{ item.seller?.shopName || item.product.shopName }}</p>
          <div class="actions">
            <button class="sm" :class="{ ok: !item.product.carouselFlag }"
                    @click="toggleCarousel(item.product.id, !item.product.carouselFlag)">
              {{ item.product.carouselFlag ? '⭐ 取消轮播' : '☆ 设为轮播' }}
            </button>
            <button class="sm danger" v-if="item.product.status==='PUBLISHED'" @click="adminTakeDown(item.product.id)">强制下架</button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="!carouselList.length" class="empty">暂无商品</div>
  </div>

  <!-- 订单 -->
  <div v-else-if="tab==='orders'" class="panel">
    <table class="tbl">
      <thead><tr><th>订单号</th><th>商品</th><th>买家</th><th>卖家</th><th>金额</th><th>积分抵扣</th><th>状态</th><th>时间</th></tr></thead>
      <tbody>
        <tr v-for="o in allOrders" :key="o.id">
          <td>{{ o.orderNo }}</td>
          <td>{{ o.productName }}</td>
          <td>{{ o.buyerId }}</td>
          <td>{{ o.sellerId }}</td>
          <td>￥{{ o.amount }}</td>
          <td>{{ o.pointsDeducted || 0 }}</td>
          <td><span class="status-tag" :class="o.status">{{ o.status }}</span></td>
          <td>{{ new Date(o.createdAt).toLocaleString() }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- 黑名单 -->
  <div v-else-if="tab==='blacklist'" class="panel">
    <div class="toolbar">
      <select v-model="blacklistForm.buyerId">
        <option value="">选择要拉黑的买家</option>
        <option v-for="u in users.filter(x => x.role==='USER' && !blacklistRows.some(b => b.blacklist.buyerId===x.id))" :key="u.id"
                :value="u.id">#{{ u.id }} {{ u.username }}（{{ u.realName || '未填姓名' }}）</option>
      </select>
      <input v-model="blacklistForm.reason" placeholder="拉黑原因">
      <button class="btn-primary" @click="addBlacklist">➕ 平台拉黑</button>
    </div>
    <p class="sub" style="margin-top:0">被平台拉黑的买家无法在任意店铺下单；商家也可在自己后台拉黑买家（仅该店铺生效）。</p>
    <table class="tbl">
      <thead><tr><th>ID</th><th>买家</th><th>范围</th><th>原因</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="b in blacklistRows" :key="b.blacklist.id">
          <td>{{ b.blacklist.id }}</td>
          <td>#{{ b.blacklist.buyerId }} {{ b.buyer?.username || '' }}（{{ b.buyer?.realName || '—' }}）</td>
          <td><span class="tag">{{ b.blacklist.scope === 'SITE' ? '全平台' : '店铺' }}</span></td>
          <td>{{ b.blacklist.reason || '—' }}</td>
          <td>{{ b.blacklist.createdAt ? new Date(b.blacklist.createdAt).toLocaleString() : '—' }}</td>
          <td><button class="sm danger" @click="removeBlacklist(b.blacklist.id)">移除</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="!blacklistRows.length" class="empty">暂无黑名单记录</div>
  </div>

  <!-- 商家评价 -->
  <div v-else-if="tab==='merchants'" class="panel">
    <table class="tbl">
      <thead><tr><th>ID</th><th>店铺</th><th>商家</th><th>审核</th><th>等级</th><th>好评率</th><th>服务评价</th><th>订单</th><th>商品</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="s in merchantStats" :key="s.merchant.id">
          <td>{{ s.merchant.id }}</td>
          <td>{{ s.merchant.shopName || '—' }}</td>
          <td>{{ s.merchant.realName || s.merchant.username }}</td>
          <td><span class="audit" :class="s.merchant.auditStatus">{{ s.merchant.auditStatus }}</span></td>
          <td>Lv.{{ s.merchant.merchantLevel || '-' }}</td>
          <td><b :class="{ good: s.goodRate >= 80, bad: s.goodRate < 60 }">{{ s.goodRate != null ? s.goodRate + '%' : '—' }}</b></td>
          <td>{{ s.serviceAvg }} 星 / {{ s.serviceReviewCount }} 条</td>
          <td>{{ s.orderCount }}</td>
          <td>{{ s.productCount }}</td>
          <td class="actions">
            <button class="sm" @click="openUserDetail(s.merchant.id)">📋 评价明细</button>
            <button class="sm" @click="setLevel(s.merchant.id)">💎 调级</button>
            <button class="sm danger" v-if="s.merchant.status==='ACTIVE'" @click="closeShop(s.merchant.id)">🏪 关闭店铺</button>
            <button class="sm ok" v-else @click="openShop(s.merchant.id)">🏪 恢复店铺</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!merchantStats.length" class="empty">暂无商家</div>
    <p class="sub" style="margin-top:12px">好评率 = 商家收到评价的平均星级 × 20；服务评价来自买家对订单的服务态度评价。可结合好评率调整商家等级（影响结算手续费率）。</p>
  </div>

  <!-- 商家等级 -->
  <div v-else-if="tab==='levels'" class="panel">
    <table class="tbl">
      <thead><tr><th>等级</th><th>费率</th><th>说明</th></tr></thead>
      <tbody>
        <tr v-for="l in levels" :key="l.levelNo">
          <td><b>Lv.{{ l.levelNo }}</b></td>
          <td>{{ (l.feeRate * 100).toFixed(2) }}%</td>
          <td>{{ l.description }}</td>
        </tr>
      </tbody>
    </table>
    <p class="sub" style="margin-top:14px">管理员在【用户管理】中对商家点击 💎 按钮即可设置等级。</p>
  </div>

  <!-- 注销审核 -->
  <div v-else-if="tab==='cancel'" class="panel">
    <p class="sub" style="margin-top:0">用户提交注销申请后需在这里审核；通过后账号将被停用、无法登录，商家的在售商品会自动下架。</p>
    <table class="tbl">
      <thead><tr><th>ID</th><th>用户</th><th>原因</th><th>状态</th><th>申请时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="c in cancellations" :key="c.request.id">
          <td>{{ c.request.id }}</td>
          <td>#{{ c.request.userId }} {{ c.user?.username || '' }}（{{ c.user?.realName || '—' }} · {{ c.user?.role }}）</td>
          <td>{{ c.request.reason || '—' }}</td>
          <td><span class="audit" :class="c.request.status">{{ c.request.status }}</span></td>
          <td>{{ c.request.createdAt ? new Date(c.request.createdAt).toLocaleString() : '—' }}</td>
          <td class="actions">
            <template v-if="c.request.status==='PENDING'">
              <button class="sm ok" @click="reviewCancel(c.request.id, true)">✅ 通过</button>
              <button class="sm danger" @click="reviewCancel(c.request.id, false)">❌ 拒绝</button>
            </template>
            <span v-else class="sub">{{ c.request.adminNote || '已处理' }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!cancellations.length" class="empty">暂无注销申请</div>
  </div>

  <!-- 限时封禁弹窗 -->
  <div v-if="banOpen" class="modal-mask" @click.self="banOpen=false">
    <div class="modal" style="width:min(460px,100%)">
      <div class="modal-head">
        <h3>🚫 封禁用户 #{{ banTarget?.id }} {{ banTarget?.username }}</h3>
        <button class="sm" @click="banOpen=false">关闭</button>
      </div>
      <p class="sub">封禁期间该用户无法登录；若为商家，其全部在售商品会被自动下架，且无法发布新商品。</p>
      <div class="ban-presets">
        <button v-for="d in [3,7,30]" :key="d" :class="{ on: banForm.days===d }" @click="banForm.days=d">{{ d }} 天</button>
        <button :class="{ on: banForm.days===0 }" @click="banForm.days=0">永久</button>
      </div>
      <input v-model="banForm.reason" placeholder="封禁原因（如：售假 / 恶意骚扰）" style="width:100%;margin-top:12px">
      <div class="modal-actions">
        <button class="sm" @click="banOpen=false">取消</button>
        <button class="btn-danger" @click="submitBan">确认封禁</button>
      </div>
    </div>
  </div>

  <!-- 用户完整注册资料 -->
  <div v-if="detailOpen && userDetail" class="modal-mask" @click.self="closeDetail">
    <div class="modal">
      <div class="modal-head">
        <h3>📋 用户注册资料 #{{ userDetail.user.id }}</h3>
        <button class="sm" @click="closeDetail">关闭</button>
      </div>
      <div class="detail-grid">
        <div><label>用户名</label><span>{{ userDetail.user.username }}</span></div>
        <div><label>角色</label><span>{{ userDetail.user.role }} / {{ userDetail.user.userType }}</span></div>
        <div><label>真实姓名</label><span>{{ userDetail.user.realName || '—' }}</span></div>
        <div><label>性别</label><span>{{ userDetail.user.gender || '—' }}</span></div>
        <div><label>手机号</label><span>{{ userDetail.user.phone || '—' }}</span></div>
        <div><label>邮箱</label><span>{{ userDetail.user.email || '—' }}</span></div>
        <div><label>城市</label><span>{{ userDetail.user.city || '—' }}</span></div>
        <div><label>银行卡号</label><span>{{ userDetail.user.bankAccount || '—' }}</span></div>
        <div><label>店铺名称</label><span>{{ userDetail.user.shopName || '—' }}</span></div>
        <div><label>审核状态</label><span><span class="audit" :class="userDetail.user.auditStatus">{{ userDetail.user.auditStatus }}</span></span></div>
        <div><label>账号状态</label><span>{{ userDetail.user.status }}</span></div>
        <div><label>商家等级</label><span>{{ userDetail.user.merchantLevel || '-' }}</span></div>
        <div><label>好评率</label><span>{{ userDetail.user.goodRate != null ? userDetail.user.goodRate + '%' : '—' }}</span></div>
        <div><label>钱包余额</label><span>￥{{ userDetail.user.walletBalance }}</span></div>
        <div><label>积分</label><span>{{ userDetail.user.points }}</span></div>
        <div><label>注册时间</label><span>{{ new Date(userDetail.user.createdAt).toLocaleString() }}</span></div>
      </div>

      <div v-if="userDetail.user.licenseImage || userDetail.user.idCardImage" class="img-row">
        <div v-if="userDetail.user.licenseImage" class="img-box">
          <label>营业执照</label>
          <a :href="userDetail.user.licenseImage" target="_blank"><img :src="userDetail.user.licenseImage" alt="营业执照"></a>
        </div>
        <div v-if="userDetail.user.idCardImage" class="img-box">
          <label>身份证</label>
          <a :href="userDetail.user.idCardImage" target="_blank"><img :src="userDetail.user.idCardImage" alt="身份证"></a>
        </div>
      </div>

      <div v-if="userDetail.user.auditStatus==='PENDING'" class="modal-actions">
        <button class="btn-danger" @click="auditUser(userDetail.user.id,false); closeDetail()">❌ 拒绝</button>
        <button class="btn-primary" @click="auditUser(userDetail.user.id,true); closeDetail()">✅ 通过审核</button>
      </div>

      <div class="review-block">
        <h4>收到买家的服务评价（{{ userDetail.serviceReviews?.length || 0 }}）</h4>
        <div v-if="!userDetail.serviceReviews?.length" class="sub">暂无服务评价</div>
        <div v-for="r in userDetail.serviceReviews" :key="r.id" class="review-row">
          <span class="stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5-r.rating) }}</span>
          <span class="sub">订单 {{ r.orderNo }}</span>
          <p>{{ r.content || '（无文字评价）' }}</p>
          <span class="sub">{{ new Date(r.createdAt).toLocaleString() }}</span>
        </div>
      </div>

      <div class="review-block">
        <h4>商家对该用户的评价（{{ userDetail.buyerReviews?.length || 0 }}）</h4>
        <div v-if="!userDetail.buyerReviews?.length" class="sub">暂无商家评价</div>
        <div v-for="r in userDetail.buyerReviews" :key="r.id" class="review-row">
          <span class="stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5-r.rating) }}</span>
          <span class="sub">订单 {{ r.orderNo }}</span>
          <p>{{ r.content || '（无文字评价）' }}</p>
          <span class="sub">{{ new Date(r.createdAt).toLocaleString() }}</span>
        </div>
      </div>
    </div>
  </div>
</section>
</template>

<style scoped>
h2 { margin-top:0; }
.sub-tabs { display:flex; gap:8px; margin:14px 0 18px; flex-wrap:wrap; }
.sub-tabs button { background:white; color:#4b5563; padding:9px 15px; border-radius:9px; box-shadow:0 1px 2px rgba(16,24,40,.04); font-weight:500; }
.sub-tabs button.active { background:#edf2f7; color:#35597f; font-weight:600; }
.panel { background:white; padding:24px 26px; border-radius:14px; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); }

.stat-row { display:grid; grid-template-columns:repeat(auto-fill,minmax(140px,1fr)); gap:16px; }
.stat { background:#f8f9fa; padding:16px; border-radius:10px; text-align:center; }
.fund-box { margin-top:20px; padding:18px; border-radius:12px; background:#f6f8f7; }
.fund-title { font-weight:600; color:#35597f; margin-bottom:12px; }
.stat .n { font-size:26px; font-weight:600; color:#35597f; }
.stat .n.green { color:#155724; }
.stat .n.yellow { color:#856404; }
.stat > div:last-child { color:#718096; font-size:13px; margin-top:4px; }

.toolbar { display:flex; gap:8px; margin-bottom:14px; }
.tbl { width:100%; border-collapse:separate; border-spacing:0; font-size:13.5px; }
.tbl th { background:#f8f9fa; font-weight:600; }
.tag { padding:3px 8px; border-radius:6px; font-size:12px; background:#edf2f7; color:#35597f; }
.audit { padding:3px 8px; border-radius:4px; font-size:12px; }
.audit.PENDING { background:#fff3cd; color:#856404; }
.audit.APPROVED { background:#d4edda; color:#155724; }
.audit.REJECTED { background:#f8d7da; color:#721c24; }
.actions button { margin-right:4px; }
.sm { padding:5px 9px; font-size:12px; }
.sm.warn { background:#fbf3e4; color:#8a5b1e; }
.sm.ok { background:#e9f3ec; color:#2f6b4d; }
.sm.danger { background:#fbeaea; color:#9c3730; }

.prod-row { display:flex; gap:16px; padding:14px 16px; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.prod-row:last-child { margin-bottom:0; }
.prod-row .thumb { width:120px; height:120px; border-radius:8px; background-size:cover; background-position:center; flex-shrink:0; position:relative; }
.noimg { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:12px; pointer-events:none; }
.prod-row .info { flex:1; }
.prod-row h4 { margin:0 0 4px; }
.prod-row .desc { color:#718096; font-size:13px; max-height:40px; overflow:hidden; margin:4px 0 10px; }
.prod-row .actions { display:flex; gap:8px; }
.status-tag { padding:3px 10px; border-radius:6px; font-size:12px; background:#eef1f6; color:#39465c; }
.sub { color:#718096; font-size:13px; margin:4px 0; }
.empty { text-align:center; padding:30px; color:#718096; }

.pending-block { background:#fbf6ea; border-radius:10px; padding:14px 16px; margin-bottom:16px; }
.pending-block h4 { margin:0 0 10px; color:#8a5b1e; }
.pending-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:8px 10px; border-radius:8px; font-size:13px; }
.pending-row:nth-child(even) { background:rgba(255,255,255,.55); }
.good { color:#155724; }
.bad { color:#c0392b; }

.modal-mask { position:fixed; inset:0; background:rgba(15,23,42,.45); display:flex; align-items:center; justify-content:center; z-index:200; padding:20px; }
.modal { background:white; border-radius:14px; width:min(760px,100%); max-height:88vh; overflow-y:auto; padding:24px 26px; box-shadow:0 24px 60px rgba(16,24,40,.25); }
.modal-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.modal-head h3 { margin:0; }
.detail-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:10px 20px; }
.detail-grid > div { display:flex; gap:10px; font-size:13px; padding:8px 12px; border-radius:8px; background:#f8f9fa; }
.detail-grid label { color:#718096; min-width:66px; }
.detail-grid span { word-break:break-all; }
.img-row { display:flex; gap:18px; margin-top:16px; flex-wrap:wrap; }
.img-box label { display:block; color:#718096; font-size:13px; margin-bottom:6px; }
.img-box img { width:220px; height:140px; object-fit:cover; border-radius:8px; }
.modal-actions { display:flex; gap:10px; justify-content:flex-end; margin-top:16px; }
.review-block { margin-top:18px; }
.review-block h4 { margin:0 0 8px; }
.review-row { background:#f8f9fa; border-radius:8px; padding:10px 12px; margin-bottom:8px; }
.review-row p { margin:6px 0; font-size:13px; }
.stars { color:#f5a623; margin-right:10px; }

.carousel-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(210px,1fr)); gap:16px; }
.c-card { border:0; border-radius:12px; overflow:hidden; background:#fafbfc; }
.c-card .thumb { height:130px; background-size:cover; background-position:center; display:flex; align-items:flex-start; justify-content:flex-end; padding:8px; position:relative; }
.c-card .c-info { padding:10px 12px 12px; }
.c-card h4 { margin:0 0 4px; font-size:14px; }
.c-card .actions { display:flex; gap:6px; flex-wrap:wrap; margin-top:8px; }
.ban-presets { display:flex; gap:8px; }
.ban-presets button { padding:8px 16px; border-radius:8px; background:#f1f4f9; color:#39465c; }
.ban-presets button.on { background:#c0392b; color:white; }
</style>
