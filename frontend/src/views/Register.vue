<script setup>
import { ref, onMounted } from 'vue'
import api from '../api.js'

const props = defineProps(['onGoto', 'onNotice'])
const goto = props.onGoto
const notice = props.onNotice

const form = ref({
  username: '', password: '', confirmPassword: '',
  realName: '', phone: '', email: '', city: '', gender: '', bankAccount: '',
  shopName: '', userType: 'PERSONAL',
  captchaKey: '', captchaCode: '',
})
const captchaImg = ref('')
const licenseFile = ref(null)
const idFile = ref(null)
const loading = ref(false)
const showPwd = ref(false)
const showPwd2 = ref(false)

async function refreshCaptcha() {
  try {
    const r = await api.get('/captcha')
    form.value.captchaKey = r.data.data.captchaKey
    captchaImg.value = r.data.data.image
    form.value.captchaCode = ''
  } catch (e) {
    notice('验证码加载失败，请检查后端是否启动', 'error')
  }
}

function uploadFile(file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post('/upload', fd).then(r => r.data.data.url)
}

function validate() {
  if (!form.value.username.trim()) return '请填写登录账号'
  if (!form.value.password) return '请设置登录密码'
  if (form.value.password.length < 6) return '密码长度不能少于 6 位'
  if (!form.value.confirmPassword) return '请再次输入密码'
  if (form.value.password !== form.value.confirmPassword) return '两次输入的密码不一致'
  if (!form.value.realName.trim()) return '请填写真实姓名'
  if (!form.value.phone.trim()) return '请填写手机号'
  if (!/^1\d{10}$/.test(form.value.phone.trim())) return '手机号格式不正确，应为 11 位数字'
  if (!form.value.email.trim()) return '请填写邮箱'
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(form.value.email.trim())) return '邮箱格式不正确'
  if (!form.value.city.trim()) return '请填写所在城市'
  if (!form.value.gender) return '请选择性别'
  if (!form.value.bankAccount.trim()) return '请填写 16 位银行卡号'
  if (!/^\d{16}$/.test(form.value.bankAccount.trim())) return '银行卡号必须为 16 位数字'
  if (!form.value.captchaCode.trim()) return '请填写图片验证码'
  if (form.value.userType === 'MERCHANT') {
    if (!form.value.shopName.trim()) return '商家请填写店铺名称'
    if (!licenseFile.value) return '商家请上传营业执照图片'
    if (!idFile.value) return '商家请上传身份证图片'
  }
  return ''
}

async function submit() {
  const err = validate()
  if (err) return notice(err, 'error')
  loading.value = true
  try {
    const body = { ...form.value }
    delete body.confirmPassword
    if (body.userType === 'MERCHANT') {
      body.licenseImage = await uploadFile(licenseFile.value)
      body.idCardImage = await uploadFile(idFile.value)
    }
    await api.post('/auth/register', body)
    goto('login')
    notice('注册申请已提交 ✅ 请等待管理员审核，审核通过后即可登录', 'info', 5000)
  } catch (e) {
    notice(e.message, 'error')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
<div class="auth-wrap">
  <div class="auth-card">
    <aside class="auth-side">
      <div class="side-logo">📝</div>
      <h1>加入校园<br>二手市场</h1>
      <p>注册后需管理员审核，审核通过即可登录</p>
      <ul>
        <li>👤 个人用户：浏览、购买、退货、评价</li>
        <li>🏪 商家：开店、发布商品、管理订单</li>
        <li>🔐 实名资料仅用于平台审核</li>
      </ul>
    </aside>

    <section class="auth-form">
      <h2>创建账号</h2>
      <p class="auth-sub">带 * 为必填项</p>

      <div class="role-switch">
        <label :class="{active: form.userType==='PERSONAL'}" @click="form.userType='PERSONAL'">👤 个人用户</label>
        <label :class="{active: form.userType==='MERCHANT'}" @click="form.userType='MERCHANT'">🏪 商家</label>
      </div>

      <form novalidate @submit.prevent="submit">
        <div class="two-col">
          <div>
            <label>登录账号 *</label>
            <input v-model.trim="form.username" placeholder="用于登录的用户名">
            <label>登录密码 *</label>
            <div class="pwd-wrap">
              <input v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="至少 6 位">
              <button type="button" class="pwd-eye" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁️' }}</button>
            </div>
            <label>确认密码 *</label>
            <div class="pwd-wrap">
              <input v-model="form.confirmPassword" :type="showPwd2 ? 'text' : 'password'" placeholder="再次输入密码">
              <button type="button" class="pwd-eye" @click="showPwd2 = !showPwd2">{{ showPwd2 ? '🙈' : '👁️' }}</button>
            </div>
            <label>真实姓名 *</label>
            <input v-model.trim="form.realName" placeholder="用于实名审核">
            <label>手机号 *</label>
            <input v-model.trim="form.phone" maxlength="11" placeholder="11 位手机号">
            <label>邮箱 *</label>
            <input v-model.trim="form.email" type="email" placeholder="example@mail.com">
          </div>
          <div>
            <label>所在城市 *</label>
            <input v-model.trim="form.city" placeholder="如：北京">
            <label>性别 *</label>
            <select v-model="form.gender">
              <option value="">请选择</option>
              <option value="男">男</option>
              <option value="女">女</option>
            </select>
            <label>银行卡号 *</label>
            <input v-model.trim="form.bankAccount" maxlength="16" placeholder="16 位数字">

            <template v-if="form.userType === 'MERCHANT'">
              <label>店铺名称 *</label>
              <input v-model.trim="form.shopName" placeholder="XX 校园小店">
              <label>营业执照图片 *</label>
              <input type="file" accept="image/*" @change="e => licenseFile = e.target.files[0]">
              <label>身份证图片 *</label>
              <input type="file" accept="image/*" @change="e => idFile = e.target.files[0]">
            </template>

            <div class="captcha-row">
              <label>图片验证码 *</label>
              <div class="captcha-box">
                <input v-model.trim="form.captchaCode" maxlength="4" placeholder="输入右侧字符">
                <img :src="captchaImg" @click="refreshCaptcha" title="点击刷新验证码">
              </div>
            </div>
          </div>
        </div>

        <button class="btn-primary full" :disabled="loading" type="submit">
          {{ loading ? '提交中…' : '提交注册申请' }}
        </button>
        <p class="tip">已有账号？<a @click="goto('login')">去登录</a></p>
      </form>
    </section>
  </div>
</div>
</template>

<style scoped>
.auth-wrap { min-height:70vh; display:grid; place-items:center; padding:20px 0; }
.auth-card {
  width:1020px; max-width:100%; display:grid; grid-template-columns:0.85fr 1.4fr;
  background:#fff; border-radius:16px; overflow:hidden;
  box-shadow:0 1px 2px rgba(16,24,40,.04), 0 18px 48px rgba(16,24,40,.10);
}
.auth-side {
  background:#243447;
  color:#fff; padding:46px 34px; display:flex; flex-direction:column; justify-content:center;
}
.side-logo { font-size:34px; }
.auth-side h1 { font-size:24px; line-height:1.62; margin:24px 0 14px; font-weight:600; letter-spacing:.5px; color:#fff; }
.auth-side p { color:rgba(255,255,255,.86); margin:0 0 22px; font-size:13px; }
.auth-side ul { list-style:none; padding:0; margin:0; display:grid; gap:12px; font-size:13px; color:rgba(255,255,255,.94); }
.auth-form { padding:36px 38px; }
.auth-form h2 { margin:0 0 4px; font-size:22px; font-weight:600; color:#141a22; }
.auth-sub { color:#5d6878; font-size:12.5px; margin:0 0 16px; }
.role-switch { display:flex; gap:12px; margin-bottom:14px; }
.role-switch label {
  flex:1; text-align:center; padding:11px; background:#f5f6f8; border-radius:10px; cursor:pointer;
  border:1px solid transparent; font-weight:600; font-size:13.5px; color:#526080; transition:background .16s;
}
.role-switch label.active { background:#edf2f7; border-color:#d6e0ea; color:#35597f; }
.two-col { display:grid; grid-template-columns:1fr 1fr; gap:0 24px; }
label { display:block; margin-top:13px; margin-bottom:5px; font-size:12.5px; color:#232d3b; font-weight:600; }
input, select { width:100%; }
.pwd-wrap { position:relative; }
.pwd-wrap input { padding-right:44px; }
.pwd-eye {
  position:absolute; right:6px; top:50%; transform:translateY(-50%);
  width:36px; height:36px; display:flex; align-items:center; justify-content:center;
  background:transparent; color:#526080; font-size:16px; padding:0; z-index:2; border-radius:8px;
}
.pwd-eye:hover { background:rgba(53,89,127,.08); box-shadow:none; }
.btn-primary.full { width:100%; margin-top:24px; padding:13px; font-size:16px; border-radius:11px; letter-spacing:2px; }
.captcha-row { margin-top:13px; }
.captcha-box { display:flex; gap:8px; align-items:center; }
.captcha-box input { flex:1; }
.captcha-box img { height:42px; border-radius:8px; cursor:pointer; }
.tip { text-align:center; margin-top:14px; color:#5d6878; font-size:13.5px; }
.tip a { color:#35597f; font-weight:600; }
@media (max-width:820px) {
  .auth-card { grid-template-columns:1fr; }
  .auth-side { display:none; }
  .auth-form { padding:26px 22px; }
  .two-col { grid-template-columns:1fr; }
}
</style>
