<script setup>
import { ref } from 'vue'
import api from '../api.js'
import store from '../store.js'

const props = defineProps(['onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice

const form = ref({ username: '', password: '' })
const showPwd = ref(false)
const loading = ref(false)

async function submit() {
  if (!form.value.username.trim()) return notice('请输入用户名', 'error')
  if (!form.value.password) return notice('请输入密码', 'error')
  loading.value = true
  try {
    const r = await api.post('/auth/login', form.value)
    const d = r.data?.data
    if (!d?.token || !d?.user) throw new Error('登录失败：服务器未返回登录信息，请重启后端后重试')
    store.setAuth(d)
    goto('home')
    notice('登录成功 ✅')
  } catch (e) { notice(e.message, 'error') } finally { loading.value = false }
}
</script>

<template>
<div class="auth-wrap">
  <div class="auth-card">
    <aside class="auth-side">
      <div class="side-logo">🎒</div>
      <h1>校园二手<br>交易市场</h1>
      <p>让闲置好物在校园里继续发光</p>
      <ul>
        <li>✅ 实名审核 · 交易更安心</li>
        <li>✅ 商家店铺 · 多图展示</li>
        <li>✅ 钱包积分 · 退货保障</li>
      </ul>
    </aside>

    <section class="auth-form">
      <h2>欢迎回来 👋</h2>
      <p class="auth-sub">普通用户、商家、管理员统一从此登录</p>
      <form novalidate @submit.prevent="submit">
        <label>用户名</label>
        <input v-model.trim="form.username" placeholder="请输入用户名" autocomplete="username">
        <label>密码</label>
        <div class="pwd-wrap">
          <input v-model="form.password" :type="showPwd ? 'text' : 'password'"
                 placeholder="请输入密码" autocomplete="current-password">
          <button type="button" class="pwd-eye" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁️' }}</button>
        </div>
        <button class="btn-primary full" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
        <p class="tip">
          还没有账号？<a @click="goto('register')">立即注册</a>
        </p>
      </form>
    </section>
  </div>
</div>
</template>

<style scoped>
.auth-wrap { min-height:70vh; display:grid; place-items:center; padding:20px 0; }
.auth-card {
  width:820px; max-width:100%; display:grid; grid-template-columns:1fr 1fr;
  background:#fff; border-radius:16px; overflow:hidden;
  box-shadow:0 1px 2px rgba(16,24,40,.04), 0 18px 48px rgba(16,24,40,.10);
}
.auth-side {
  background:#243447;
  color:white; padding:52px 40px; display:flex; flex-direction:column; justify-content:center;
}
.side-logo { font-size:34px; }
.auth-side h1 { font-size:26px; line-height:1.55; margin:24px 0 14px; font-weight:600; letter-spacing:.5px; color:#fff; }
.auth-side p { color:rgba(255,255,255,.86); margin:0 0 24px; font-size:13.5px; }
.auth-side ul { list-style:none; padding:0; margin:0; display:grid; gap:11px; font-size:13.5px; color:rgba(255,255,255,.94); }
.auth-form { padding:48px 44px; }
.auth-form h2 { margin:0 0 6px; font-size:22px; color:#141a22; font-weight:600; }
.auth-sub { color:#5d6878; font-size:13px; margin:0 0 24px; }
label { display:block; margin-top:16px; margin-bottom:6px; font-size:13px; color:#232d3b; font-weight:600; }
input { width:100%; }
.pwd-wrap { position:relative; }
.pwd-wrap input { padding-right:44px; }
.pwd-eye {
  position:absolute; right:6px; top:50%; transform:translateY(-50%);
  width:36px; height:36px; display:flex; align-items:center; justify-content:center;
  background:transparent; color:#526080; font-size:16px; padding:0; z-index:2; border-radius:8px;
}
.pwd-eye:hover { background:rgba(53,89,127,.08); box-shadow:none; }
.btn-primary.full { width:100%; margin-top:24px; padding:13px; font-size:16px; border-radius:11px; letter-spacing:4px; }
.tip { margin-top:18px; color:#5d6878; font-size:13.5px; text-align:center; }
.tip a { color:#35597f; font-weight:600; }
@media (max-width:720px) {
  .auth-card { grid-template-columns:1fr; }
  .auth-side { display:none; }
  .auth-form { padding:32px 24px; }
}
</style>
