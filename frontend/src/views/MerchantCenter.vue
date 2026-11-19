<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import api from '../api.js'
import store from '../store.js'

const props = defineProps(['onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice

const tab = ref('dashboard')   // dashboard / publish / products / orders / returns / negotiations
const dashboard = ref({})
const shop = ref({})
const products = ref([])
const productFilter = ref('')
const orders = ref([])
const returns = ref([])
const negotiations = ref([])
const blacklistRows = ref([])
const blForm = ref({ buyerId: '', reason: '' })

function switchTab(t) { tab.value = t; refresh() }

async function refresh() {
  // 各接口独立容错，避免某张表未创建导致整页空白
  const [dash, shopResp, ps, os, rs, ns, bs, me] = await Promise.allSettled([
    api.get('/merchant/dashboard'),
    api.get('/merchant/shop'),
    api.get('/merchant/products', { params: { status: productFilter.value || null } }),
    api.get('/orders/seller'),
    api.get('/returns/seller'),
    api.get('/merchant/negotiations'),
    api.get('/merchant/blacklists'),
    api.get('/users/me'),
  ])
  const val = r => r.status === 'fulfilled' ? (r.value.data.data || []) : null
  if (val(dash)) dashboard.value = val(dash)
  if (val(shopResp)) shop.value = val(shopResp)
  if (val(ps)) products.value = val(ps)
  if (val(os)) orders.value = val(os)
  if (val(rs)) returns.value = val(rs)
  if (val(ns)) negotiations.value = val(ns)
  if (val(bs)) blacklistRows.value = val(bs)
  if (val(me)) store.user = val(me)
}

/** 后台定时刷新各页签数量，不用点击也能看到最新数字 */
let pollTimer = null
async function refreshCounts() {
  const [dash, shopResp, os, rs, ns, bs] = await Promise.allSettled([
    api.get('/merchant/dashboard'),
    api.get('/merchant/shop'),
    api.get('/orders/seller'),
    api.get('/returns/seller'),
    api.get('/merchant/negotiations'),
    api.get('/merchant/blacklists'),
  ])
  const val = r => r.status === 'fulfilled' ? (r.value.data.data || []) : null
  if (val(dash)) dashboard.value = val(dash)
  if (val(shopResp)) shop.value = val(shopResp)
  if (val(os)) orders.value = val(os)
  if (val(rs)) returns.value = val(rs)
  if (val(ns)) negotiations.value = val(ns)
  if (val(bs)) blacklistRows.value = val(bs)
}

// ---------- 黑名单 ----------
async function addBlacklist() {
  const buyerId = Number(blForm.value.buyerId)
  if (!buyerId) return notice('请填写买家 ID', 'error')
  try {
    await api.post('/merchant/blacklists', { buyerId, reason: blForm.value.reason || '恶意购买' })
    notice('已拉黑该买家，其无法在本店下单')
    blForm.value = { buyerId: '', reason: '' }
    refresh()
  } catch(e) { notice(e.message, 'error') }
}
async function removeBlacklist(id) {
  if (!confirm('解除对该买家的拉黑？')) return
  try { await api.delete(`/merchant/blacklists/${id}`); notice('已解除'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

// ---------- 发布商品 ----------
const pubForm = ref({
  name: '', category: '', originalPrice: '', price: '', sizeInfo: '',
  description: '', negotiable: false, stock: 1, conditionLevel: '九成新',
  images: [],
})
async function publish() {
  if (!pubForm.value.name || !pubForm.value.price) return notice('请填写商品名称和价格')
  try {
    const body = { ...pubForm.value }
    body.price = Number(body.price)
    body.originalPrice = Number(body.originalPrice || body.price)
    body.stock = Number(body.stock)
    body.negotiable = body.negotiable ? 'true' : 'false'
    await api.post('/products', body)
    notice('发布成功 ✅ 等待管理员审核')
    pubForm.value = { name:'', category:'', originalPrice:'', price:'', sizeInfo:'', description:'', negotiable:false, stock:1, conditionLevel:'九成新', images: [] }
    switchTab('products')
  } catch(e) { notice(e.message, 'error') }
}
async function uploadImages(files) {
  const urls = []
  for (const f of files) {
    const fd = new FormData(); fd.append('file', f)
    const r = await api.post('/upload', fd)
    urls.push(r.data.data.url)
  }
  pubForm.value.images.push(...urls)
}
async function takeDown(id) {
  if (!confirm('确定下架此商品？')) return
  try { await api.post(`/products/${id}/take-down`); notice('已下架'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

async function republish(id) {
  if (!confirm('重新上架需要管理员重新审核，确定提交？')) return
  try { await api.post(`/products/${id}/republish`); notice('已重新提交审核 ✅'); refresh() }
  catch(e) { notice(e.message, 'error') }
}

// ---------- 编辑商品（编辑后重新送审） ----------
const editOpen = ref(false)
const editId = ref(null)
const editForm = ref({})

function openEdit(item) {
  editId.value = item.product.id
  editForm.value = {
    name: item.product.name,
    category: item.product.category,
    price: item.product.price,
    originalPrice: item.product.originalPrice,
    stock: item.product.stock,
    conditionLevel: item.product.conditionLevel,
    sizeInfo: item.product.sizeInfo,
    description: item.product.description,
    negotiable: !!item.product.negotiable,
    images: item.coverImage ? [item.coverImage] : [],
  }
  editOpen.value = true
}

async function uploadEditImages(files) {
  for (const f of files) {
    const fd = new FormData(); fd.append('file', f)
    const r = await api.post('/upload', fd)
    editForm.value.images.push(r.data.data.url)
  }
}

async function submitEdit() {
  if (!editForm.value.name || !editForm.value.price) return notice('请填写商品名称和价格', 'error')
  if (Number(editForm.value.price) <= 0) return notice('售价必须大于 0', 'error')
  if (Number(editForm.value.stock) < 0) return notice('库存不能为负数', 'error')
  try {
    await api.put(`/products/${editId.value}`, {
      ...editForm.value,
      price: Number(editForm.value.price),
      originalPrice: Number(editForm.value.originalPrice || editForm.value.price),
      stock: Number(editForm.value.stock),
      negotiable: editForm.value.negotiable ? 'true' : 'false',
    })
    notice('修改已保存，已重新提交管理员审核 ✅')
    editOpen.value = false
    productFilter.value = 'PENDING'
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// ---------- 商家评价买家（购买/退货均可） ----------
const buyerReviewOpen = ref(false)
const buyerReviewOrder = ref(null)
const buyerReviewForm = ref({ rating: 5, content: '' })

async function shipOrder(id) {
  if (!confirm('确认已发货？买家将看到「待收货」并可以确认收货')) return
  try { await api.post(`/orders/${id}/ship`); notice('已确认发货 🚚'); refresh() }
  catch (e) { notice(e.message, 'error') }
}

function canReviewBuyer(o) {
  return o.status === 'COMPLETED' || o.status === 'RETURNED'
}
function openBuyerReview(o) {
  buyerReviewOrder.value = o
  buyerReviewForm.value = { rating: 5, content: '' }
  buyerReviewOpen.value = true
}
async function submitBuyerReview() {
  if (!buyerReviewForm.value.content.trim()) return notice('请填写对该买家的评价内容', 'error')
  try {
    await api.post('/reviews', {
      orderId: buyerReviewOrder.value.id,
      reviewType: 'SELLER_TO_BUYER',
      rating: buyerReviewForm.value.rating,
      content: buyerReviewForm.value.content,
    })
    notice('买家评价已提交 ✅')
    buyerReviewOpen.value = false
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// ---------- 议价咨询回复 ----------
const negoReply = ref({ open: false, item: null, reply: '', accepted: false })
function openNegoReply(n) {
  negoReply.value = { open: true, item: n, reply: '', accepted: false }
}
async function submitNegoReply() {
  const r = negoReply.value
  if (!r.reply.trim()) return notice('请填写回复内容', 'error')
  try {
    await api.post(`/negotiations/${r.item.negotiation.id}/reply`, {
      reply: r.reply,
      accepted: r.accepted,
    })
    notice('已回复买家 ✅')
    negoReply.value = { open: false, item: null, reply: '', accepted: false }
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

// ---------- 退货审核 ----------
async function reviewReturn(r, approved) {
  const comment = prompt(approved ? '同意退货，填写备注（可选）' : '拒绝退货，填写原因') || ''
  try {
    await api.post(`/returns/${r.id}/review`, { approved, comment })
    notice(approved ? '已同意退货 ✅' : '已拒绝退货')
    refresh()
  } catch(e) { notice(e.message, 'error') }
}

onMounted(() => {
  refresh()
  pollTimer = setInterval(refreshCounts, 20000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
<section>
  <div class="header-box">
    <div>
      <h2>🏪 商家中心</h2>
      <p class="sub">店铺：{{ shop.merchant?.shopName || shop.merchant?.username || '—' }}
        · 等级 {{ shop.merchant?.merchantLevel || 1 }}
        · 好评率 {{ shop.merchant?.goodRate }}%</p>
    </div>
    <div class="bal">💰 钱包 ￥{{ shop.merchant?.walletBalance?.toFixed(2) || '0.00' }}</div>
  </div>

  <nav class="sub-tabs">
    <button :class="{active: tab==='dashboard'}" @click="switchTab('dashboard')">📊 工作台</button>
    <button :class="{active: tab==='publish'}" @click="switchTab('publish')">➕ 发布商品</button>
    <button :class="{active: tab==='products'}" @click="switchTab('products')">🛍️ 我的商品 ({{ shop.publishedCount + shop.offShelfCount + shop.pendingCount || 0 }})</button>
    <button :class="{active: tab==='orders'}" @click="switchTab('orders')">📦 出售订单 ({{ orders.length }})</button>
    <button :class="{active: tab==='returns'}" @click="switchTab('returns')">↩️ 退货审核 ({{ returns.length }})</button>
    <button :class="{active: tab==='negotiations'}" @click="switchTab('negotiations')">💬 议价咨询 ({{ negotiations.length }})</button>
    <button :class="{active: tab==='blacklists'}" @click="switchTab('blacklists')">🚫 黑名单 ({{ blacklistRows.length }})</button>
  </nav>

  <div v-if="shop.banned || shop.status==='DISABLED'" class="shop-alert">
    <b>⚠️ 店铺当前不可经营</b>
    <span v-if="shop.status==='DISABLED'">店铺已被平台暂时关闭，全部商品已下架。</span>
    <span v-else>账号已被封禁<template v-if="shop.banUntil">至 {{ new Date(shop.banUntil).toLocaleString() }}</template>，无法发布新商品。</span>
    <span v-if="shop.banReason">原因：{{ shop.banReason }}</span>
  </div>

  <!-- 工作台 -->
  <div v-if="tab==='dashboard'" class="panel">
    <div class="stat-row">
      <div class="stat">
        <div class="n">{{ shop.publishedCount || 0 }}</div><div>在售商品</div>
      </div>
      <div class="stat">
        <div class="n">{{ shop.pendingCount || 0 }}</div><div>待审核</div>
      </div>
      <div class="stat">
        <div class="n">{{ shop.offShelfCount || 0 }}</div><div>已下架</div>
      </div>
      <div class="stat">
        <div class="n">{{ shop.totalSold || 0 }}</div><div>累计销量</div>
      </div>
      <div class="stat">
        <div class="n">{{ dashboard.orderCount || 0 }}</div><div>总订单</div>
      </div>
      <div class="stat">
        <div class="n green">￥{{ (dashboard.heldAmount || 0).toFixed(2) }}</div><div>平台暂存中</div>
      </div>
    </div>
  </div>

  <!-- 发布商品 -->
  <div v-else-if="tab==='publish'" class="panel">
    <div class="two-col">
      <div>
        <label>商品名称 *</label>
        <input v-model="pubForm.name" required>
        <label>商品类别</label>
        <input v-model="pubForm.category" placeholder="如：数码/书籍/服装">
        <label>售价 * (元)</label>
        <input v-model.number="pubForm.price" type="number" min="0" step="0.01" required>
        <label>原价 (元)</label>
        <input v-model.number="pubForm.originalPrice" type="number" min="0" step="0.01">
        <label>库存数量</label>
        <input v-model.number="pubForm.stock" type="number" min="1">
        <label>新旧程度</label>
        <select v-model="pubForm.conditionLevel">
          <option>全新</option><option>九成新</option><option>八成新</option>
          <option>七成新</option><option>有划痕</option><option>功能完好</option>
        </select>
      </div>
      <div>
        <label>尺寸大小</label>
        <input v-model="pubForm.sizeInfo">
        <label>使用说明</label>
        <textarea v-model="pubForm.description" rows="4"></textarea>
        <label>
          <input type="checkbox" v-model="pubForm.negotiable"> 允许议价
        </label>
        <label>商品图片（可多选）</label>
        <input type="file" multiple accept="image/*" @change="e => uploadImages(e.target.files)">
        <div v-if="pubForm.images.length" class="img-list">
          <div v-for="(url,i) in pubForm.images" :key="i" class="img-t" :style="{ backgroundImage: 'url('+url+')' }">
            <button @click="pubForm.images.splice(i,1)">×</button>
          </div>
        </div>
      </div>
    </div>
    <button class="btn-primary full" @click="publish">提交商品审核</button>
  </div>

  <!-- 我的商品 -->
  <div v-else-if="tab==='products'" class="panel">
    <div class="filter-row">
      <button :class="{active: productFilter===''}" @click="productFilter='';refresh()">全部</button>
      <button :class="{active: productFilter==='PUBLISHED'}" @click="productFilter='PUBLISHED';refresh()">在售</button>
      <button :class="{active: productFilter==='PENDING'}" @click="productFilter='PENDING';refresh()">待审核</button>
      <button :class="{active: productFilter==='OFF_SHELF'}" @click="productFilter='OFF_SHELF';refresh()">已下架</button>
      <button :class="{active: productFilter==='SOLD'}" @click="productFilter='SOLD';refresh()">已售出</button>
      <button :class="{active: productFilter==='REJECTED'}" @click="productFilter='REJECTED';refresh()">被驳回</button>
    </div>
    <div v-if="!products.length" class="empty">暂无商品</div>
    <div class="grid">
      <article v-for="item in products" :key="item.product.id" class="mp-card">
        <div class="thumb" :style="{ backgroundImage: item.coverImage ? 'url('+item.coverImage+')' : 'none', backgroundColor: item.coverImage ? 'transparent' : '#eef0f3' }">
          <span v-if="!item.coverImage" class="noimg">暂无图片</span>
          <span class="tag">{{ item.product.status }}</span>
        </div>
        <div class="info">
          <h4>{{ item.product.name }}</h4>
          <p class="sub">￥{{ item.product.price }} · 库存 {{ item.product.stock }} · 已售 {{ item.product.soldCount }}</p>
          <p v-if="item.product.auditReason" class="sub red">理由：{{ item.product.auditReason }}</p>
          <div class="actions">
            <button v-if="item.product.status!=='LOCKED'" class="sm-btn" @click="openEdit(item)">✏️ 编辑</button>
            <button v-if="item.product.status==='PUBLISHED'" class="btn-danger" @click="takeDown(item.product.id)">下架</button>
            <button v-if="item.product.status==='OFF_SHELF' || item.product.status==='REJECTED'" class="btn-primary" @click="republish(item.product.id)">重新上架</button>
          </div>
        </div>
      </article>
    </div>
  </div>

  <!-- 出售订单 -->
  <div v-else-if="tab==='orders'" class="panel">
    <div v-if="!orders.length" class="empty">暂无订单</div>
    <div v-for="o in orders" :key="o.id" class="order-item">
      <div class="ohd">
        <b>{{ o.productName }}</b>
        <span class="status-tag" :class="o.status">{{ o.status }}</span>
      </div>
      <p class="sub">订单号 {{ o.orderNo }} · 数量 {{ o.quantity }} · ￥{{ o.amount }}</p>
      <p class="sub">购买者ID {{ o.buyerId }} · 创建于 {{ new Date(o.createdAt).toLocaleString() }}</p>
      <div class="actions">
        <button v-if="o.status==='PAID_PENDING_RECEIVE'" class="btn-primary" @click="shipOrder(o.id)">🚚 确认发货</button>
        <button v-if="canReviewBuyer(o)" class="btn-primary" @click="openBuyerReview(o)">✍️ 评价买家</button>
      </div>
    </div>
  </div>

  <!-- 退货审核 -->
  <div v-else-if="tab==='returns'" class="panel">
    <div v-if="!returns.length" class="empty">暂无退货申请</div>
    <div v-for="r in returns" :key="r.id" class="order-item">
      <div class="ohd"><b>订单 {{ r.orderNo }}</b>
        <span class="status-tag" :class="r.status">{{ r.status }}</span>
      </div>
      <p class="sub">买家ID {{ r.buyerId }} · 原因：{{ r.reason }}</p>
      <div class="actions">
        <button v-if="r.status==='PENDING'" class="btn-ghost" @click="reviewReturn(r, false)">❌ 拒绝</button>
        <button v-if="r.status==='PENDING'" class="btn-primary" @click="reviewReturn(r, true)">✅ 同意退货</button>
      </div>
    </div>
  </div>

  <!-- 议价咨询 -->
  <div v-else-if="tab==='negotiations'" class="panel">
    <div v-if="!negotiations.length" class="empty">暂无买家议价/留言</div>
    <div v-for="n in negotiations" :key="n.negotiation.id" class="order-item">
      <div class="ohd">
        <b>{{ n.product?.name || '商品已删除' }}</b>
        <span class="status-tag" :class="n.negotiation.status">{{ n.negotiation.status }}</span>
      </div>
      <p class="sub">买家：{{ n.buyer?.username }} · 商品售价 ￥{{ n.product?.price }}</p>
      <p class="sub" v-if="n.negotiation.offerPrice">买家出价：<b class="price">￥{{ n.negotiation.offerPrice }}</b></p>
      <p class="sub" v-if="n.negotiation.message">买家留言：{{ n.negotiation.message }}</p>
      <p class="reply" v-if="n.negotiation.reply">我的回复：{{ n.negotiation.reply }}</p>
      <div class="actions">
        <button v-if="n.negotiation.status==='PENDING'" class="btn-primary" @click="openNegoReply(n)">💬 回复买家</button>
      </div>
    </div>
  </div>

  <!-- 黑名单 -->
  <div v-else-if="tab==='blacklists'" class="panel">
    <div class="toolbar">
      <input v-model="blForm.buyerId" placeholder="买家 ID（可在出售订单中查看）" style="width:220px">
      <input v-model="blForm.reason" placeholder="拉黑原因">
      <button class="btn-primary" @click="addBlacklist">🚫 拉黑买家</button>
    </div>
    <p class="sub" style="margin-top:0">被本店拉黑的买家无法在本店下单（不影响其他店铺）。</p>
    <table class="tbl">
      <thead><tr><th>ID</th><th>买家</th><th>原因</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="b in blacklistRows" :key="b.blacklist.id">
          <td>{{ b.blacklist.id }}</td>
          <td>#{{ b.blacklist.buyerId }} {{ b.buyer?.username || '' }}（{{ b.buyer?.realName || '—' }}）</td>
          <td>{{ b.blacklist.reason || '—' }}</td>
          <td>{{ b.blacklist.createdAt ? new Date(b.blacklist.createdAt).toLocaleString() : '—' }}</td>
          <td><button class="sm-btn danger" @click="removeBlacklist(b.blacklist.id)">解除</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="!blacklistRows.length" class="empty">暂无拉黑记录</div>
  </div>

  <!-- 编辑商品弹窗 -->
  <div v-if="editOpen" class="modal-bg" @click.self="editOpen=false">
    <div class="modal">
      <h3>✏️ 编辑商品</h3>
      <p class="sub">保存后商品会重新提交管理员审核，审核通过前不会在商城展示。</p>
      <div class="two-col">
        <div>
          <label>商品名称 *</label>
          <input v-model="editForm.name">
          <label>类别</label>
          <input v-model="editForm.category">
          <label>售价 *</label>
          <input type="number" min="0.01" step="0.01" v-model="editForm.price">
          <label>原价</label>
          <input type="number" min="0" step="0.01" v-model="editForm.originalPrice">
          <label>库存</label>
          <input type="number" min="0" v-model="editForm.stock">
          <label>新旧程度</label>
          <select v-model="editForm.conditionLevel">
            <option>全新</option><option>九成新</option><option>八成新</option>
            <option>七成新</option><option>有划痕</option><option>功能完好</option>
          </select>
        </div>
        <div>
          <label>尺寸</label>
          <input v-model="editForm.sizeInfo">
          <label>使用说明</label>
          <textarea v-model="editForm.description" rows="4"></textarea>
          <label><input type="checkbox" v-model="editForm.negotiable"> 允许议价</label>
          <label>商品图片</label>
          <input type="file" multiple accept="image/*" @change="e => uploadEditImages(e.target.files)">
          <div v-if="editForm.images?.length" class="img-list">
            <div v-for="(url,i) in editForm.images" :key="i" class="img-t" :style="{ backgroundImage: 'url('+url+')' }">
              <button @click="editForm.images.splice(i,1)">×</button>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-actions">
        <button @click="editOpen=false">取消</button>
        <button class="btn-primary" @click="submitEdit">保存并重新送审</button>
      </div>
    </div>
  </div>

  <!-- 评价买家弹窗 -->
  <div v-if="buyerReviewOpen" class="modal-bg" @click.self="buyerReviewOpen=false">
    <div class="modal">
      <h3>✍️ 评价买家</h3>
      <p class="sub">订单：{{ buyerReviewOrder?.productName }} · 状态 {{ buyerReviewOrder?.status }}</p>
      <label>评分</label>
      <div class="stars-rating">
        <span v-for="i in 5" :key="i" :class="{active: i<=buyerReviewForm.rating}" @click="buyerReviewForm.rating=i">★</span>
      </div>
      <label>评价内容</label>
      <textarea v-model="buyerReviewForm.content" rows="3" placeholder="评价该买家的购买/退货行为"></textarea>
      <div class="modal-actions">
        <button @click="buyerReviewOpen=false">取消</button>
        <button class="btn-primary" @click="submitBuyerReview">提交</button>
      </div>
    </div>
  </div>

  <!-- 回复议价弹窗 -->
  <div v-if="negoReply.open" class="modal-bg" @click.self="negoReply.open=false">
    <div class="modal">
      <h3>💬 回复买家</h3>
      <p class="sub" v-if="negoReply.item?.negotiation?.offerPrice">
        买家出价 ￥{{ negoReply.item.negotiation.offerPrice }}（原价 ￥{{ negoReply.item.product?.price }}）
      </p>
      <label>回复内容</label>
      <textarea v-model="negoReply.reply" rows="3" placeholder="如：可以按 80 元成交，请直接下单后联系我改价"></textarea>
      <label><input type="checkbox" v-model="negoReply.accepted"> 同意按买家出价成交</label>
      <div class="modal-actions">
        <button @click="negoReply.open=false">取消</button>
        <button class="btn-primary" @click="submitNegoReply">发送</button>
      </div>
    </div>
  </div>
</section>
</template>

<style scoped>
.header-box { display:flex; justify-content:space-between; align-items:center; background:white; padding:20px 24px; border-radius:14px; box-shadow:0 5px 22px rgba(29,53,87,.06); }
.header-box h2 { margin:0; }
.sub { color:#718096; margin:4px 0; font-size:13px; }
.bal { font-size:18px; font-weight:bold; color:#35597f; }
.sub-tabs { display:flex; gap:8px; margin:20px 0; flex-wrap:wrap; }
.sub-tabs button { background:white; color:#4b5563; padding:9px 15px; border-radius:9px; box-shadow:0 1px 2px rgba(16,24,40,.04); font-weight:500; }
.sub-tabs button.active { background:#edf2f7; color:#35597f; font-weight:600; }
.panel { background:white; padding:24px 26px; border-radius:14px; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); }

.stat-row { display:grid; grid-template-columns:repeat(auto-fill,minmax(140px,1fr)); gap:16px; }
.stat { background:#f8f9fa; padding:16px; border-radius:10px; text-align:center; }
.stat .n { font-size:26px; font-weight:600; color:#35597f; }
.stat .n.green { color:#155724; }
.stat > div:last-child { color:#718096; font-size:13px; margin-top:4px; }

.two-col { display:grid; grid-template-columns:1fr 1fr; gap:20px; }
label { display:block; margin-top:12px; font-size:13px; color:#39465c; }
input, select, textarea { width:100%; margin-top:4px; }
.btn-primary.full { width:100%; margin-top:20px; padding:13px; font-size:16px; }
.img-list { display:flex; flex-wrap:wrap; gap:8px; margin-top:8px; }
.img-t { width:80px; height:80px; border-radius:6px; background-size:cover; background-position:center; position:relative; }
.img-t button { position:absolute; top:-8px; right:-8px; width:22px; height:22px; padding:0; border-radius:50%; font-size:14px; }

.filter-row { display:flex; gap:8px; margin-bottom:16px; flex-wrap:wrap; }
.filter-row button { padding:8px 14px; }
.filter-row button.active { background:#edf2f7; color:#35597f; font-weight:600; }

.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:16px; }
.mp-card { background:#fafbfc; border-radius:12px; overflow:hidden; border:0; }
.mp-card .thumb { height:140px; background-size:cover; background-position:center; position:relative; }
.noimg { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:12px; pointer-events:none; }
.mp-card .tag { position:absolute; top:8px; right:8px; padding:3px 8px; border-radius:6px; font-size:12px; background:rgba(255,255,255,.92); color:#35597f; }
.mp-card .info { padding:12px 14px; }
.mp-card h4 { margin:0 0 6px; font-size:15px; }
.red { color:#721c24; }
.actions { display:flex; gap:8px; margin-top:8px; }
.btn-danger { background:#b0473f; color:white; }
.empty { text-align:center; padding:40px; color:#718096; }

.order-item { padding:14px 16px; border:0; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.order-item:last-child { margin-bottom:0; }
.ohd { display:flex; justify-content:space-between; align-items:center; }
.status-tag { padding:3px 10px; border-radius:6px; font-size:12px; background:#eef1f6; color:#39465c; }
.status-tag.PAID_PENDING_RECEIVE { background:#fff3cd; color:#856404; }
.status-tag.SHIPPED { background:#d1ecf1; color:#0c5460; }
.status-tag.COMPLETED { background:#d4edda; color:#155724; }
.status-tag.RETURN_PENDING { background:#f8d7da; color:#721c24; }
.sm-btn { background:#e6eaf2; color:#39465c; }
.sm-btn.danger { background:#fdecec; color:#c0392b; }
.shop-alert { display:flex; gap:14px; flex-wrap:wrap; align-items:center; background:#fdecec; border:1px solid #f5c2c2;
  color:#a12525; border-radius:10px; padding:12px 18px; margin-bottom:14px; font-size:14px; }
.toolbar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px; }
.tbl { width:100%; border-collapse:separate; border-spacing:0; font-size:13.5px; }
.tbl th { background:#f8f9fa; font-weight:600; }
.reply { background:#f6f8fa; border-left:3px solid #35597f; padding:8px 12px; border-radius:6px; font-size:13px; margin:8px 0; }
.status-tag.REPLIED { background:#d1ecf1; color:#0c5460; }
.status-tag.ACCEPTED { background:#d4edda; color:#155724; }
.status-tag.RETURNED { background:#e2e3e5; color:#383d41; }

.modal-bg { position:fixed; inset:0; background:rgba(0,0,0,.4); display:grid; place-items:center; z-index:1000; overflow:auto; }
.modal { background:white; padding:24px; border-radius:14px; width:720px; max-width:94%; max-height:90vh; overflow:auto; }
.modal h3 { margin-top:0; }
.modal-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:18px; }
.stars-rating span { font-size:28px; cursor:pointer; color:#ddd; }
.stars-rating span.active { color:#f5a623; }
.price { color:#9c4a3c; }
@media (max-width:760px) { .two-col { grid-template-columns:1fr; } }
</style>
