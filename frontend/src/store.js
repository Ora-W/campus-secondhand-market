// 简单全局状态：登录信息、当前视图
import { reactive } from 'vue'

const state = reactive({
  token: localStorage.getItem('token') || '',
  user: JSON.parse(localStorage.getItem('user') || 'null'),

  setAuth({ token, user }) {
    this.token = token
    this.user = user
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
  },
  clearAuth() {
    this.token = ''
    this.user = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  },
  // 角色判断
  isLoggedIn() { return !!this.token },
  isAdmin() { return this.token && this.token.endsWith(':ADMIN') },
  isMerchant() { return this.token && this.token.endsWith(':MERCHANT') },
})

export default state
