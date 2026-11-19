<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import api from '../api.js'

const props = defineProps(['onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice || (() => {})

const keyword = ref('')
const sort = ref('')
const products = ref([])
const carousel = ref([])
const loading = ref(false)
const activeSlide = ref(0)
let slideTimer = null

async function load() {
  loading.value = true
  try {
    const [cp, ps] = await Promise.all([
      api.get('/products/carousel').catch(() => ({ data: { data: [] } })),
      api.get('/products', { params: { keyword: keyword.value || null, sort: sort.value || null } }),
    ])
    carousel.value = cp.data.data || []
    products.value = ps.data.data || []
    if (activeSlide.value >= carousel.value.length) activeSlide.value = 0
  } catch (e) {
    notice(e.message, 'error')
  } finally {
    loading.value = false
  }
}

function slideImg(item) {
  return item.product?.carouselImage || item.coverImage || ''
}
function step(n) {
  if (!carousel.value.length) return
  activeSlide.value = (activeSlide.value + n + carousel.value.length) % carousel.value.length
}

onMounted(() => {
  load()
  slideTimer = setInterval(() => step(1), 4000)
})
onUnmounted(() => { if (slideTimer) clearInterval(slideTimer) })
</script>

<template>
<section>
  <!-- 轮播图：不同商家的推荐位 / 广告宣传商品 -->
  <div v-if="carousel.length" class="carousel">
    <div class="slides">
      <div v-for="(item, i) in carousel" :key="item.product?.id || i"
           class="slide" :class="{ active: i === activeSlide }"
           :style="{ backgroundImage: slideImg(item) ? 'url('+slideImg(item)+')' : 'none', backgroundColor: slideImg(item) ? 'transparent' : '#e7ebf0' }"
           @click="goto('detail', item.product?.id)">
        <span class="ad-tag">🔥 商家推荐</span>
        <div class="slide-info">
          <h2>{{ item.product?.name }}</h2>
          <p>￥{{ item.product?.price }} · 店铺：{{ item.seller?.shopName || item.product?.shopName }}</p>
          <p class="sub-line">{{ item.product?.description || '校园好物推荐' }}</p>
        </div>
      </div>
    </div>
    <button v-if="carousel.length > 1" class="arrow prev" @click.stop="step(-1)">‹</button>
    <button v-if="carousel.length > 1" class="arrow next" @click.stop="step(1)">›</button>
    <div v-if="carousel.length > 1" class="dots">
      <span v-for="(item, i) in carousel" :key="'d'+i" :class="{ on: i === activeSlide }" @click.stop="activeSlide = i"></span>
    </div>
  </div>

  <!-- 搜索 -->
  <div class="toolbar">
    <input v-model="keyword" @keyup.enter="load" placeholder="🔍 搜索商品名称...">
    <select v-model="sort" @change="load">
      <option value="">最新发布</option>
      <option value="price_asc">价格 ↑</option>
      <option value="price_desc">价格 ↓</option>
      <option value="sales">销量优先</option>
      <option value="rating">好评率优先</option>
    </select>
    <button class="btn-primary" @click="load">搜索</button>
  </div>

  <!-- 商品网格 -->
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else-if="!products.length" class="empty">暂无商品</div>
  <div v-else class="grid">
    <article v-for="item in products" :key="item.product.id" class="card" @click="goto('detail', item.product.id)">
      <div class="thumb" :style="{ backgroundImage: item.coverImage ? 'url('+item.coverImage+')' : 'none', backgroundColor: item.coverImage ? 'transparent' : '#eef0f3' }">
        <span v-if="!item.coverImage" class="noimg">暂无图片</span>
        <span v-if="item.product?.negotiable" class="badge">可议价</span>
      </div>
      <div class="info">
        <span class="tag">{{ item.product?.category || '校园好物' }}</span>
        <h3>{{ item.product.name }}</h3>
        <p class="desc">{{ item.product.description || '暂无描述' }}</p>
        <div class="price-row">
          <strong>￥{{ item.product.price }}</strong>
          <del v-if="item.product.originalPrice && item.product.originalPrice > item.product.price" class="origin">￥{{ item.product.originalPrice }}</del>
        </div>
        <div class="meta">
          <small>{{ item.product.conditionLevel || '九成新' }}</small>
          <small>库存 {{ item.product.stock }} · 已售 {{ item.product.soldCount }}</small>
        </div>
        <div v-if="item.seller" class="seller-row">🏪 {{ item.seller.shopName || item.seller.username }} · 好评率 {{ item.seller.goodRate }}%</div>
      </div>
    </article>
  </div>
</section>
</template>

<style scoped>
.carousel { position:relative; margin-bottom:24px; }
.carousel .slides { position:relative; height:240px; border-radius:14px; overflow:hidden; }
.carousel .slide {
  position:absolute; inset:0; border-radius:14px; background-size:cover; background-position:center;
  cursor:pointer; opacity:0; transition:opacity .6s ease; pointer-events:none;
}
.carousel .slide.active { opacity:1; pointer-events:auto; }
.ad-tag { position:absolute; top:14px; left:14px; background:rgba(156,55,48,.92); color:white;
  padding:4px 12px; border-radius:99px; font-size:12px; font-weight:600; }
.carousel .slide-info { position:absolute; bottom:0; left:0; right:0; padding:18px 22px;
  background:linear-gradient(transparent, rgba(0,0,0,.72)); color:white; }
.carousel h2 { margin:0; font-size:22px; }
.carousel p { margin:5px 0 0; opacity:.92; font-size:13px; }
.carousel .sub-line { max-width:70%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.carousel .arrow { position:absolute; top:50%; transform:translateY(-50%); width:34px; height:34px;
  border-radius:50%; background:rgba(0,0,0,.45); color:white; font-size:22px; line-height:1; z-index:3; cursor:pointer; }
.carousel .arrow.prev { left:12px; }
.carousel .arrow.next { right:12px; }
.carousel .dots { position:absolute; bottom:10px; right:16px; display:flex; gap:6px; z-index:3; }
.carousel .dots span { width:8px; height:8px; border-radius:99px; background:rgba(255,255,255,.5); cursor:pointer; }
.carousel .dots span.on { width:20px; background:white; }

.toolbar { display:flex; gap:10px; margin-bottom:18px; }
.toolbar input { flex:1; }

.loading, .empty { text-align:center; padding:60px; color:#718096; }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:18px; }
.card { background:white; border-radius:14px; overflow:hidden; box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.05); cursor:pointer; transition:box-shadow .18s; }
.card:hover { box-shadow:0 2px 4px rgba(16,24,40,.05), 0 12px 30px rgba(16,24,40,.09); }
.thumb { height:160px; display:grid; place-items:center; background-size:cover; background-position:center; position:relative; }
.noimg { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:12px; pointer-events:none; }
.badge { position:absolute; top:10px; right:10px; background:#9c3730; color:white; padding:3px 10px; border-radius:99px; font-size:12px; }
.info { padding:14px 16px 16px; }
.info h3 { margin:8px 0; font-size:16px; }
.desc { color:#718096; min-height:38px; font-size:13px; margin:0 0 8px; }
.price-row { display:flex; align-items:baseline; gap:6px; }
.info strong { color:#9c4a3c; font-size:22px; }
.origin { color:#aaa; font-size:13px; }
.meta { display:flex; justify-content:space-between; color:#718096; font-size:12px; margin:8px 0; }
.seller-row { margin-top:6px; padding:6px 10px; border-radius:7px; background:#f6f8fa; font-size:13px; color:#35597f; }
</style>
