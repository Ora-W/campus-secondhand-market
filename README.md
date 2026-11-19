# 校园二手交易市场

技术栈：Java 17、Spring Boot 3.3、MyBatis Plus、MySQL 8、Vue 3、Vite、HTML、CSS、JavaScript。

## 启动前准备
- JDK 17+
- Maven 3.8+
- MySQL 8+
- Node.js 18+

## 1. 初始化数据库
创建数据库并执行 `database/schema.sql`。默认数据库名 `campus_market`，可在 `backend/src/main/resources/application.yml` 修改连接信息。

## 2. 启动后端
```bash
cd backend
mvn spring-boot:run
```
后端地址：`http://localhost:8080`
健康检查：`GET http://localhost:8080/api/health`

首次启动会自动创建演示管理员：
- 用户名：`admin`
- 密码：`admin123`
请仅在本地演示环境使用，部署前必须修改。

## 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```
访问 Vite 输出的地址（通常为 `http://localhost:5173`）。

## 当前版本说明
这是可运行的课程项目基础实现，包含数据库结构、前后端工程、演示登录、用户审核、商品审核/发布/下架、商品搜索排序、购物车、下单钱包扣款、积分、收货确认、超时自动结算、24小时退货申请/审核、评价和管理员充值等基础流程。

商家注册资料图片使用本地上传目录保存；图片接口返回相对访问路径。系统没有接入真实银行或第三方支付。中间账户是本地数据库账务记录，不代表真实资金托管。

部分业务边界（例如多商家购物车拆单、等级动态调整阈值、评价资格和拉黑范围）需要结合课程演示规则继续完善。请在真实运行中逐项测试，不要把未测试功能写成已通过。
