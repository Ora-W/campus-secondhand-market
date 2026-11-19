<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api.js'
import store from '../store.js'

const props = defineProps(['productId', 'onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice

const data = ref(null)         // { product, images, seller, reviewAvg, reviewCount }
const activeImgIdx = ref(0)
const reviews = ref([])

const myReview = ref({ rating: 5, content: '', reviewType: 'BUYER_TO_PRODUCT' })
const myReviewAttitude = ref({ rating: 5, content: '' })

function cover() {
  if (data.value?.images?.length) return data.value.images[0].imageUrl
  return data.value?.product?.carouselImage || ''
}

async function load() {
  try {
    const r = await api.get(`/products/${props.productId}`)
    data.value = r.data.data
    const rv = await api.get(`/products/${props.productId}/reviews`)
    reviews.value = rv.data.data || []
  } catch (e) {
    notice(e.message, 'error')
  }
}

async function addCart() {
  if (!store.isLoggedIn()) { notice('请先登录'); return }
  try {
    await api.post(`/cart/${props.productId}`)
    notice('已加入购物车 ✅')
  } catch (e) { notice(e.message, 'error') }
}

async function submitProductReview() {
  try {
    await api.post('/reviews', {
      orderId: myReview.value.orderId,
      rating: myReview.value.rating,
      content: myReview.value.content,
      reviewType: 'BUYER_TO_PRODUCT',
    })
    notice('评价已提交')
    myReview.value = { rating: 5, content: '' }
    load()
  } catch (e) { notice(e.message, 'error') }
}

// 议价 / 联系商家
const showNego = ref(false)
const negoForm = ref({ offerPrice: '', message: '' })

function openNego() {
  if (!store.isLoggedIn()) return notice('请先登录后再联系商家', 'error')
  negoForm.value = { offerPrice: '', message: '' }
  showNego.value = true
}

async function submitNego() {
  const price = negoForm.value.offerPrice === '' ? null : Number(negoForm.value.offerPrice)
  if (price !== null && (!price || price <= 0)) return notice('期望价格必须大于 0', 'error')
  if (price !== null && price >= Number(data.value.product.price))
    return notice(`期望价格需低于当前售价 ￥${data.value.product.price}`, 'error')
  if (price === null && !negoForm.value.message.trim())
    return notice('请填写期望价格或留言内容', 'error')
  try {
    await api.post('/negotiations', {
      productId: props.productId,
      offerPrice: price,
      message: negoForm.value.message,
    })
    notice('已发送给商家，等待回复 ✅')
    showNego.value = false
  } catch (e) { notice(e.message, 'error') }
}

onMounted(load)
</script>

<template>
<div v-if="!data" class="center">加载中...</div>
<section v-else class="detail">
  <button class="btn-ghost" @click="goto('home')">← 返回首页</button>

  <div class="main">
    <div class="imgs">
      <div class="big" :style="{ backgroundImage: (data.images?.length ? data.images[activeImgIdx].imageUrl : cover()) ? 'url('+(data.images?.length ? data.images[activeImgIdx].imageUrl : cover())+')' : 'none', backgroundColor: (data.images?.length ? data.images[activeImgIdx].imageUrl : cover()) ? 'transparent' : '#eef0f3' }">
        <span v-if="!(data.images?.length ? data.images[activeImgIdx].imageUrl : cover())" class="noimg">暂无图片</span>
      </div>
      <div v-if="data.images?.length > 1" class="thumbs">
        <div v-for="(img, i) in data.images" :key="img.id"
             class="t" :class="{active: i===activeImgIdx}"
             :style="{ backgroundImage: 'url('+img.imageUrl+')' }"
             @click="activeImgIdx = i"></div>
      </div>
    </div>

    <div class="info">
      <span class="tag">{{ data.product.category || '校园好物' }}</span>
      <h1>{{ data.product.name }}</h1>
      <p class="desc">{{ data.product.description }}</p>
      <div class="price-row">
        <span class="price">￥{{ data.product.price }}</span>
        <del v-if="data.product.originalPrice && data.product.originalPrice > data.product.price" class="origin">￥{{ data.product.originalPrice }}</del>
      </div>
      <div class="meta">
        <span>📦 库存 {{ data.product.stock }}</span>
        <span>🏷️ 新旧：{{ data.product.conditionLevel }}</span>
        <span v-if="data.product.negotiable">💬 允许议价</span>
      </div>
      <div class="shop-box" v-if="data.seller">
        <h3>🏪 店铺</h3>
        <p><b>{{ data.seller.shopName || data.seller.username }}</b></p>
        <p>好评率 {{ data.seller.goodRate }}% · 等级 {{ data.seller.merchantLevel || 1 }} 级</p>
      </div>
      <div class="buy-row">
        <button class="btn-primary" :disabled="data.product.stock<=0" @click="addCart">
          {{ data.product.stock<=0 ? '已售罄' : '🛒 加入购物车' }}
        </button>
        <button class="btn-ghost" @click="openNego">
          {{ data.product.negotiable ? '💬 联系商家 / 议价' : '💬 联系商家' }}
        </button>
      </div>
    </div>
  </div>

  <!-- 议价弹窗 -->
  <div v-if="showNego" class="modal-bg" @click.self="showNego=false">
    <div class="modal">
      <h3>💬 联系商家</h3>
      <p class="sub">商品：{{ data.product.name }} · 当前售价 ￥{{ data.product.price }}</p>
      <label>{{ data.product.negotiable ? '我的期望价格（元，可留空只留言）' : '该商品未开启议价，可直接留言咨询' }}</label>
      <input v-if="data.product.negotiable" type="number" min="0.01" step="0.01" v-model="negoForm.offerPrice" placeholder="如 80">
      <label>留言</label>
      <textarea v-model="negoForm.message" rows="3" placeholder="想咨询的问题，例如：能否便宜一点？配件齐全吗？"></textarea>
      <div class="modal-actions">
        <button @click="showNego=false">取消</button>
        <button class="btn-primary" @click="submitNego">发送</button>
      </div>
    </div>
  </div>

  <!-- 评价统计 -->
  <div class="block">
    <h3>商品评价（{{ data.reviewCount }}条 · 平均 {{ data.reviewAvg }} 星）</h3>
    <div v-if="!reviews.length" class="empty">暂无评价</div>
    <div v-else class="reviews">
      <div v-for="rv in reviews" :key="rv.id" class="review">
        <div class="stars">{{ '⭐'.repeat(rv.rating) }}{{ '☆'.repeat(5-rv.rating) }}</div>
        <p>{{ rv.content || '（未填写文字评价）' }}</p>
        <small>— {{ new Date(rv.createdAt).toLocaleString() }}</small>
      </div>
    </div>
  </div>
</section>
</template>

<style scoped>
.center { text-align:center; padding:80px; }
.btn-ghost { margin-bottom:16px; }
.main { display:grid; grid-template-columns: 1fr 1fr; gap:30px; background:white; padding:24px; border-radius:14px; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); }
.imgs .big { width:100%; height:400px; border-radius:10px; background-size:cover; background-position:center; position:relative; }
.noimg { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:14px; pointer-events:none; }
.imgs .thumbs { display:flex; gap:8px; margin-top:10px; }
.imgs .t { width:72px; height:72px; border-radius:6px; background-size:cover; background-position:center; border:2px solid transparent; cursor:pointer; }
.imgs .t.active { border-color:#35597f; }
.info h1 { margin:10px 0; font-size:24px; }
.desc { color:#718096; line-height:1.7; }
.price-row { margin:16px 0; }
.price { font-size:32px; color:#9c4a3c; font-weight:600; }
.origin { color:#aaa; margin-left:10px; }
.meta { display:flex; gap:18px; color:#718096; font-size:14px; margin:10px 0 18px; }
.shop-box { margin:18px 0; padding:16px; background:#f6f8fa; border-radius:10px; }
.shop-box h3 { margin:0 0 6px; font-size:15px; color:#35597f; }
.shop-box p { margin:4px 0; font-size:13px; color:#718096; }
.btn-primary { padding:14px 28px; font-size:16px; }
.buy-row { display:flex; gap:10px; flex-wrap:wrap; }
.buy-row .btn-ghost { padding:14px 22px; font-size:16px; }

.modal-bg { position:fixed; inset:0; background:rgba(0,0,0,.4); display:grid; place-items:center; z-index:1000; }
.modal { background:white; padding:24px; border-radius:14px; width:440px; max-width:92%; box-shadow:0 24px 60px rgba(16,24,40,.25); }
.modal h3 { margin-top:0; }
.modal label { display:block; margin-top:12px; font-size:13px; color:#39465c; }
.modal input, .modal textarea { width:100%; margin-top:4px; }
.modal-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:16px; }
.sub { color:#718096; font-size:13px; margin:4px 0; }

.block { background:white; border-radius:14px; padding:24px 26px; margin-top:20px; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); }
.block h3 { margin-top:0; }
.reviews .review { padding:12px 14px; border-radius:10px; background:#fafbfc; margin-bottom:8px; }
.reviews .review:last-child { margin-bottom:0; }
.stars { color:#f5a623; font-size:16px; }
.review p { margin:4px 0; }
.empty { color:#718096; padding:20px; text-align:center; }
@media (max-width:760px) { .main { grid-template-columns:1fr; } }
</style>
