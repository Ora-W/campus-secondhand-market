// 统一 axios 封装：baseURL / token header / 错误处理
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['X-User-Token'] = token
  return config
})

api.interceptors.response.use(
  resp => {
    // 后端业务失败统一返回 HTTP 200 + success:false，这里转成异常，
    // 否则验证码错误、库存不足等提示会被当成成功继续执行。
    const body = resp.data
    if (body && body.success === false) {
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return resp
  },
  err => {
    let msg = err.response?.data?.message
    if (!msg) {
      if (err.code === 'ECONNABORTED' || /timeout/i.test(err.message || '')) msg = '请求超时，请稍后重试'
      else if (/network error/i.test(err.message || '')) msg = '无法连接服务器，请确认后端已启动'
      else if (/status code/i.test(err.message || '')) msg = '服务器异常，请稍后重试'
      else msg = '请求失败，请稍后重试'
    }
    return Promise.reject(new Error(msg))
  }
)

export default api
