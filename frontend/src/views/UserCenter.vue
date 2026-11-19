<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '../api.js'
import store from '../store.js'

const props = defineProps(['onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice

const tab = ref('cart')   // cart / orders / returns / negotiations / wallet / points
const cart = ref([])
const orders = ref([])
const returns = ref([])
const negotiations = ref([])
const walletRecords = ref([])
const pointRecords = ref([])
const cancelInfo = ref(null)

const checkoutPoints = ref(0)   // 下单想使用的积分

async function refresh() {
  const [me, c, o, r, n] = await Promise.allSettled([
    api.get('/users/me'), api.get('/cart'), api.get('/orders'), api.get('/returns'), api.get('/negotiations'),
  ])
  const val = x => x.status === 'fulfilled' ? (x.value.data.data || []) : null
  if (val(me)) store.user = val(me)
  if (val(c)) cart.value = val(c)
  if (val(o)) orders.value = val(o)
  if (val(r)) returns.value = val(r)
  if (val(n)) negotiations.value = val(n)
  if (tab.value === 'wallet') walletRecords.value = (await api.get(`/users/${store.user?.id}/wallet/records`)).data.data || []
  if (tab.value === 'points') pointRecords.value = (await api.get(`/users/${store.user?.id}/point/records`)).data.data || []
  if (tab.value === 'settings') await loadCancel()
}

async function loadCancel() {
  try { cancelInfo.value = (await api.get('/users/me/cancel')).data.data } catch(e) { cancelInfo.value = null }
}

async function requestCancel() {
  const reason = prompt('请填写注销原因（可选）:') || ''
  if (!confirm('注销账号需管理员审核，审核通过后账号将无法登录，确定提交申请？')) return
  try {
    await api.post('/users/me/cancel', { reason })
    notice('注销申请已提交，等待管理员审核 ✅')
    loadCancel()
  } catch(e) { notice(e.message, 'error') }
}

function logout() {
  store.clearAuth()
  goto('home')
}

/** 定时刷新数量，无需点击页签也能看到最新数字 */
let pollTimer = null
async function refreshCounts() {
  const [c, o, r, n] = await Promise.allSettled([
    api.get('/cart'), api.get('/orders'), api.get('/returns'), api.get('/negotiations'),
  ])
  const val = x => x.status === 'fulfilled' ? (x.value.data.data || []) : null
  if (val(c)) cart.value = val(c)
  if (val(o)) orders.value = val(o)
  if (val(r)) returns.value = val(r)
  if (val(n)) negotiations.value = val(n)
}

function switchTab(t) { tab.value = t; refresh() }

async function updateQty(cartId, qty, stock) {
  if (qty <= 0) {
    await api.delete(`/cart/${cartId}`)
  } else {
    if (stock != null && qty > stock) return notice(`库存不足：该商品仅剩 ${stock} 件`, 'error')
    try {
      await api.put(`/cart/${cartId}`, null, { params: { quantity: qty } })
    } catch(e) { return notice(e.message, 'error') }
  }
  refresh()
}

async function clearCart() {
  await api.delete('/cart')
  refresh()
}

const totalMoney = computed(() =>
  cart.value.reduce((s, c) => s + (c.product?.price || 0) * c.quantity, 0))

async function checkout() {
  if (!cart.value.length) return notice('购物车为空', 'error')
  const pts = Number(checkoutPoints.value) || 0
  const owned = store.user?.points || 0
  if (pts < 0) return notice('积分不能为负数', 'error')
  if (pts > owned) return notice(`积分不足：您当前仅有 ${owned} 积分，最多可使用 ${owned} 积分`, 'error')
  if (pts % 100 !== 0) return notice(`积分需按 100 的整数倍使用（100 积分 = 1 元），请输入 ${Math.floor(pts/100)*100} 或 ${(Math.floor(pts/100)+1)*100}`, 'error')
  if (pts > canUsePoints.value) return notice(`本单最多可用 ${canUsePoints.value} 积分抵扣（订单金额 ￥${totalMoney.value.toFixed(2)}）`, 'error')
  const pay = totalMoney.value - pts / 100
  if (pay > (store.user?.walletBalance || 0)) {
    return notice(`钱包余额不足：本单需支付 ￥${pay.toFixed(2)}，当前余额 ￥${(store.user?.walletBalance || 0).toFixed(2)}`, 'error')
  }
  try {
    const r = await api.post('/orders/checkout', { pointsUsed: pts })
    notice(`下单成功 ✅ 共 ${r.data.data.length} 笔订单`)
    checkoutPoints.value = 0
    switchTab('orders')
  } catch(e) { notice(e.message, 'error') }
}

async function confirmOrder(id) {
  try { await api.post(`/orders/${id}/confirm`); notice('已确认收货 ✅'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

const returnReason = ref('')
async function applyReturn(orderId) {
  if (!confirm('确定申请退货吗？')) return
  try {
    await api.post(`/orders/${orderId}/return`, { reason: returnReason.value || '商品与描述不符' })
    notice('退货申请已提交，等待商家审核')
    returnReason.value = ''
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// 评价弹窗（买家）
const reviewOrderId = ref(null)
const reviewForm = ref({ rating: 5, content: '', reviewType: 'BUYER_TO_PRODUCT' })
async function openReview(orderId) {
  reviewOrderId.value = orderId
  reviewForm.value = { rating: 5, content: '', reviewType: 'BUYER_TO_PRODUCT' }
}
async function submitReview() {
  if (!reviewForm.value.content || !reviewForm.value.content.trim()) {
    if (reviewForm.value.reviewType !== 'BUYER_TO_PRODUCT') return notice('请填写评价内容', 'error')
  }
  try {
    await api.post('/reviews', { ...reviewForm.value, orderId: reviewOrderId.value })
    notice('评价已提交 ✅')
    reviewOrderId.value = null
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// 可评价：COMPLETED 或 RETURNED
function canReview(o) {
  return (o.status === 'COMPLETED' || o.status === 'RETURNED') && o.buyerId === store.user?.id
}

const canUsePoints = computed(() => {
  if (!cart.value.length) return 0
  const total = totalMoney.value
  const maxMoney = Math.min(store.user?.points || 0, Math.floor(total * 100)) / 100
  return Math.floor(maxMoney * 100)  // 最大可抵扣的积分数
})

onMounted(() => {
  refresh()
  pollTimer = setInterval(refreshCounts, 20000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
<section>
  <div class="user-header">
    <div class="avatar">{{ (store.user?.username || 'U').charAt(0).toUpperCase() }}</div>
    <div class="info">
      <h2>{{ store.user?.realName || store.user?.username }}</h2>
      <p class="sub">💰 钱包：<b>￥{{ store.user?.walletBalance?.toFixed(2) }}</b> · ⭐ 积分：<b>{{ store.user?.points }}</b> · 好评率 {{ store.user?.goodRate }}%</p>
      <p class="sub" v-if="store.isMerchant()">🏪 店铺：{{ store.user?.shopName || '—' }} · 等级 {{ store.user?.merchantLevel || 1 }}</p>
    </div>
  </div>

  <nav class="sub-tabs">
    <button :class="{active: tab==='cart'}" @click="switchTab('cart')">🛒 购物车 ({{ cart.length }})</button>
    <button :class="{active: tab==='orders'}" @click="switchTab('orders')">📦 我的订单 ({{ orders.length }})</button>
    <button :class="{active: tab==='returns'}" @click="switchTab('returns')">↩️ 退货 ({{ returns.length }})</button>
    <button :class="{active: tab==='negotiations'}" @click="switchTab('negotiations')">💬 我的议价 ({{ negotiations.length }})</button>
    <button :class="{active: tab==='wallet'}" @click="switchTab('wallet')">💰 钱包流水</button>
    <button :class="{active: tab==='points'}" @click="switchTab('points')">⭐ 积分流水</button>
    <button :class="{active: tab==='settings'}" @click="switchTab('settings')">⚙️ 账号设置</button>
  </nav>

  <!-- 购物车 -->
  <div v-if="tab==='cart'" class="panel">
    <div v-if="!cart.length" class="empty">购物车空空如也 🛍️</div>
    <div v-else>
      <div v-for="c in cart" :key="c.cartId" class="cart-item">
        <div class="thumb" :style="{ backgroundImage: 'url(' + (c.product?.carouselImage || '') + ')' }"></div>
        <div class="flex-1">
          <h4>{{ c.product?.name }}</h4>
          <p class="sub">💰 ￥{{ c.product?.price }} · 🏪 {{ c.seller?.shopName || c.seller?.username }}</p>
        </div>
        <div class="qty">
          <button @click="updateQty(c.cartId, c.quantity - 1, c.product?.stock)">-</button>
          <span>{{ c.quantity }}</span>
          <button @click="updateQty(c.cartId, c.quantity + 1, c.product?.stock)">+</button>
        </div>
        <div class="sub">小计 ￥{{ ((c.product?.price || 0) * c.quantity).toFixed(2) }}</div>
      </div>
      <div class="checkout-bar">
        <div>
          <label>使用积分抵扣（当前 {{ store.user?.points || 0 }} 分，本单最多 {{ canUsePoints }} 分 = ￥{{ (canUsePoints/100).toFixed(2) }}，须为 100 的整数倍）</label>
          <input type="number" min="0" step="100" v-model.number="checkoutPoints" class="points-input">
          <span class="sub" style="margin-left:8px">需支付：￥{{ Math.max(0, totalMoney - (Number(checkoutPoints)||0)/100).toFixed(2) }}</span>
        </div>
        <div>
          <button class="btn-ghost" @click="clearCart">清空购物车</button>
          <button class="btn-primary" @click="checkout">一键下单</button>
        </div>
      </div>
    </div>
  </div>

  <!-- 订单 -->
  <div v-else-if="tab==='orders'" class="panel">
    <div v-if="!orders.length" class="empty">暂无订单 📦</div>
    <div v-for="o in orders" :key="o.id" class="order-item">
      <div class="ohd">
        <span><b>{{ o.productName }}</b></span>
        <span class="status-tag" :class="o.status">{{ o.status }}</span>
      </div>
      <p class="sub">订单号 {{ o.orderNo }} · ￥{{ o.amount }}
        <span v-if="o.pointsDeducted">（原 ￥{{ o.originalAmount }} · 积分抵 {{ o.pointsDeducted }} 分）</span>
      </p>
      <p class="sub">创建于 {{ new Date(o.createdAt).toLocaleString() }}</p>
      <div class="actions">
        <button v-if="(o.status==='PAID_PENDING_RECEIVE' || o.status==='SHIPPED') && o.buyerId===store.user.id" class="btn-primary" @click="confirmOrder(o.id)">✅ 确认收货</button>
        <button v-if="canReview(o)" class="btn-primary" @click="openReview(o.id)">✍️ 评价商品 / 商家</button>
        <button v-if="o.status==='COMPLETED' && o.buyerId===store.user.id" @click="applyReturn(o.id)">↩️ 申请退货</button>
      </div>
    </div>
  </div>

  <!-- 退货 -->
  <div v-else-if="tab==='returns'" class="panel">
    <div v-if="!returns.length" class="empty">暂无退货记录</div>
    <div v-for="r in returns" :key="r.id" class="order-item">
      <div class="ohd"><b>订单 {{ r.orderNo }}</b>
        <span class="status-tag" :class="r.status">{{ r.status }}</span>
      </div>
      <p class="sub">原因：{{ r.reason }}</p>
      <p class="sub">提交于 {{ new Date(r.createdAt).toLocaleString() }}</p>
    </div>
  </div>

  <!-- 我的议价 -->
  <div v-else-if="tab==='negotiations'" class="panel">
    <div v-if="!negotiations.length" class="empty">还没有议价/留言记录</div>
    <div v-for="n in negotiations" :key="n.negotiation.id" class="order-item">
      <div class="ohd">
        <b>{{ n.product?.name || '商品已删除' }}</b>
        <span class="status-tag" :class="n.negotiation.status">{{ n.negotiation.status }}</span>
      </div>
      <p class="sub">店铺：{{ n.seller?.shopName || n.seller?.username }} · 售价 ￥{{ n.product?.price }}</p>
      <p class="sub" v-if="n.negotiation.offerPrice">我的出价：<b>￥{{ n.negotiation.offerPrice }}</b></p>
      <p class="sub" v-if="n.negotiation.message">我的留言：{{ n.negotiation.message }}</p>
      <p class="reply" v-if="n.negotiation.reply">商家回复：{{ n.negotiation.reply }}</p>
      <p class="sub">提交于 {{ new Date(n.negotiation.createdAt).toLocaleString() }}</p>
    </div>
  </div>

  <!-- 钱包流水 -->
  <div v-else-if="tab==='wallet'" class="panel">
    <div v-if="!walletRecords.length" class="empty">暂无流水</div>
    <div v-for="r in walletRecords" :key="r.id" class="log-item">
      <div>
        <p><b>{{ r.type }}</b> · {{ r.remark }}</p>
        <p class="sub">{{ new Date(r.createdAt).toLocaleString() }} {{ r.orderNo ? '· 订单 ' + r.orderNo : '' }}</p>
      </div>
      <div :class="['amt', r.amount >= 0 ? 'in' : 'out']">
        {{ r.amount >= 0 ? '+' : '' }}￥{{ Math.abs(r.amount).toFixed(2) }}
      </div>
    </div>
  </div>

  <!-- 积分流水 -->
  <div v-else-if="tab==='points'" class="panel">
    <div v-if="!pointRecords.length" class="empty">暂无积分记录</div>
    <div v-for="r in pointRecords" :key="r.id" class="log-item">
      <div>
        <p><b>{{ r.type }}</b> · {{ r.remark }}</p>
        <p class="sub">{{ new Date(r.createdAt).toLocaleString() }} {{ r.orderNo ? '· 订单 ' + r.orderNo : '' }}</p>
      </div>
      <div :class="['amt', r.type==='EARN' ? 'in' : 'out']">
        {{ r.type==='EARN' ? '+' : '-' }}{{ r.points }}
      </div>
    </div>
  </div>

  <!-- 账号设置 -->
  <div v-else-if="tab==='settings'" class="panel">
    <h3 class="settings-title">账号信息</h3>
    <div class="settings-grid">
      <div><label>用户名</label><span>{{ store.user?.username }}</span></div>
      <div><label>真实姓名</label><span>{{ store.user?.realName || '—' }}</span></div>
      <div><label>手机号</label><span>{{ store.user?.phone || '—' }}</span></div>
      <div><label>邮箱</label><span>{{ store.user?.email || '—' }}</span></div>
      <div><label>城市</label><span>{{ store.user?.city || '—' }}</span></div>
      <div><label>钱包余额</label><span>￥{{ store.user?.walletBalance }}</span></div>
    </div>

    <div class="cancel-box">
      <div>
        <h4>注销账号</h4>
        <p class="sub">注销申请需管理员审核；审核通过后账号将被停用、无法登录，商家的在售商品会全部下架。</p>
        <p v-if="cancelInfo" class="cancel-state" :class="cancelInfo.status">
          当前状态：{{ { PENDING:'待管理员审核', APPROVED:'已通过（账号已停用）', REJECTED:'已被拒绝' }[cancelInfo.status] || cancelInfo.status }}
          <span v-if="cancelInfo.adminNote">· 管理员备注：{{ cancelInfo.adminNote }}</span>
        </p>
        <p v-else class="sub">尚未提交注销申请。</p>
      </div>
      <button class="btn-danger" :disabled="cancelInfo && (cancelInfo.status==='PENDING' || cancelInfo.status==='APPROVED')"
              @click="requestCancel">
        {{ cancelInfo && cancelInfo.status==='PENDING' ? '审核中…' : '申请注销账号' }}
      </button>
    </div>

    <div class="logout-row">
      <button class="btn-ghost" @click="logout">退出登录</button>
    </div>
  </div>

  <!-- 评价弹窗 -->
  <div v-if="reviewOrderId" class="modal-bg" @click.self="reviewOrderId=null">
    <div class="modal">
      <h3>写评价</h3>
      <label>评价类型</label>
      <select v-model="reviewForm.reviewType">
        <option value="BUYER_TO_PRODUCT">商品评价</option>
        <option value="BUYER_TO_SELLER">商家服务态度评价</option>
      </select>
      <label>评分</label>
      <div class="stars-rating">
        <span v-for="i in 5" :key="i" :class="{active: i<=reviewForm.rating}" @click="reviewForm.rating=i">★</span>
      </div>
      <label>文字评价</label>
      <textarea v-model="reviewForm.content" rows="3" placeholder="说说你的感受..." />
      <div class="modal-actions">
        <button @click="reviewOrderId=null">取消</button>
        <button class="btn-primary" @click="submitReview">提交</button>
      </div>
    </div>
  </div>
</section>
</template>

<style scoped>
.user-header { display:flex; align-items:center; gap:20px; background:white; padding:20px 24px; border-radius:14px; box-shadow:0 5px 22px rgba(29,53,87,.06); }
.avatar { width:60px; height:60px; border-radius:50%; background:#35597f; color:white; display:grid; place-items:center; font-size:28px; font-weight:bold; }
.user-header h2 { margin:0; }
.sub { color:#718096; margin:4px 0; font-size:13px; }
.settings-title { margin:0 0 14px; }
.settings-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:12px 22px; margin-bottom:20px; }
.settings-grid > div { display:flex; gap:10px; font-size:13.5px; padding:9px 12px; border-radius:8px; background:#f8f9fa; }
.settings-grid label { color:#7d8aa3; min-width:64px; }
.cancel-box { display:flex; align-items:center; justify-content:space-between; gap:20px; flex-wrap:wrap;
  background:#fdf4f3; border-radius:12px; padding:18px 20px; }
.cancel-box h4 { margin:0 0 4px; color:#b3261e; }
.cancel-state { font-size:13px; font-weight:600; margin:8px 0 0; }
.cancel-state.PENDING { color:#8a5b00; }
.cancel-state.APPROVED { color:#b3261e; }
.cancel-state.REJECTED { color:#526080; }
.logout-row { margin-top:22px; text-align:right; }

.sub-tabs { display:flex; gap:8px; margin:20px 0; flex-wrap:wrap; }
.sub-tabs button { background:white; color:#4b5563; padding:9px 15px; border-radius:9px; box-shadow:0 1px 2px rgba(16,24,40,.04); font-weight:500; }
.sub-tabs button.active { background:#edf2f7; color:#35597f; font-weight:600; }
.panel { background:white; padding:24px 26px; border-radius:14px; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); }
.empty { text-align:center; padding:50px; color:#718096; }

.cart-item { display:flex; align-items:center; gap:14px; padding:12px 14px; border:0; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.cart-item:last-of-type { margin-bottom:0; }
.cart-item .thumb { width:64px; height:64px; border-radius:8px; background:#eef1f6; background-size:cover; background-position:center; }
.flex-1 { flex:1; }
.cart-item h4 { margin:0 0 4px; }
.qty { display:flex; align-items:center; gap:8px; }
.qty button { width:28px; height:28px; padding:0; border-radius:6px; }
.checkout-bar { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-top:16px; padding:14px 16px; border-radius:10px; background:#f6f8fa; }
.points-input { width:120px; margin-left:8px; }

.order-item { padding:14px 16px; border:0; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.order-item:last-child { margin-bottom:0; }
.ohd { display:flex; justify-content:space-between; align-items:center; }
.status-tag { padding:3px 10px; border-radius:6px; font-size:12px; background:#eef1f6; color:#39465c; }
.status-tag.PAID_PENDING_RECEIVE { background:#fff3cd; color:#856404; }
.status-tag.SHIPPED { background:#d1ecf1; color:#0c5460; }
.status-tag.COMPLETED { background:#d4edda; color:#155724; }
.status-tag.RETURN_PENDING { background:#f8d7da; color:#721c24; }
.status-tag.RETURNED { background:#e2e3e5; color:#383d41; }
.status-tag.REPLIED { background:#d1ecf1; color:#0c5460; }
.status-tag.ACCEPTED { background:#d4edda; color:#155724; }
.reply { background:#f6f8fa; border-left:3px solid #35597f; padding:8px 12px; border-radius:6px; font-size:13px; margin:8px 0; }
.actions { display:flex; gap:8px; margin-top:8px; flex-wrap:wrap; }
.actions button { padding:7px 13px; font-size:13px; }

.log-item { display:flex; justify-content:space-between; align-items:center; padding:12px 14px; border:0; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.log-item:last-child { margin-bottom:0; }
.amt { font-weight:bold; font-size:16px; }
.amt.in { color:#155724; }
.amt.out { color:#721c24; }

.modal-bg { position:fixed; inset:0; background:rgba(0,0,0,.4); display:grid; place-items:center; z-index:1000; }
.modal { background:white; padding:24px; border-radius:14px; width:420px; max-width:92%; box-shadow:0 24px 60px rgba(16,24,40,.22); }
.modal label { display:block; margin-top:10px; font-size:13px; }
.modal textarea { width:100%; margin-top:4px; }
.modal-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:16px; }
.stars-rating span { font-size:28px; cursor:pointer; color:#ddd; }
.stars-rating span.active { color:#f5a623; }
</style>
