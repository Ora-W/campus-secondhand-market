-- =============================================================
-- 校园二手交易平台 数据库初始化脚本
-- MySQL 8+ 字符集 utf8mb4
-- =============================================================
CREATE DATABASE IF NOT EXISTS campus_market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_market;

-- ---------- 用户表 ----------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
 password VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密',
 real_name VARCHAR(50),
 phone VARCHAR(20),
 email VARCHAR(100),
 city VARCHAR(80),
 gender VARCHAR(10),
 bank_account VARCHAR(16) COMMENT '16位银行卡号',
 role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER / MERCHANT / ADMIN',
 user_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL / MERCHANT / ADMIN',
 audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
 license_image VARCHAR(500) COMMENT '商家营业执照',
 id_card_image VARCHAR(500) COMMENT '商家身份证',
 shop_name VARCHAR(100) COMMENT '商家店铺名称',
 wallet_balance DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '钱包余额',
 points BIGINT NOT NULL DEFAULT 0 COMMENT '积分(1元=1分，100分抵1元)',
 merchant_level INT COMMENT '商家等级 1-5',
 good_rate DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '商家好评率',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
 ban_until DATETIME COMMENT '限时封禁到期时间（为空表示未封禁）',
 ban_reason VARCHAR(500) COMMENT '封禁原因',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_user_role(role), INDEX idx_user_audit(audit_status), INDEX idx_user_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 商家等级与费率 ----------
DROP TABLE IF EXISTS merchant_levels;
CREATE TABLE merchant_levels (
 level_no INT PRIMARY KEY COMMENT '等级编号 1-5',
 fee_rate DECIMAL(8,5) NOT NULL COMMENT '费率 0.001~0.01',
 description VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO merchant_levels(level_no, fee_rate, description) VALUES
(1, 0.001,  '一级商家 费率0.1%'),
(2, 0.002,  '二级商家 费率0.2%'),
(3, 0.005,  '三级商家 费率0.5%'),
(4, 0.0075, '四级商家 费率0.75%'),
(5, 0.01,   '五级商家 费率1%');

-- ---------- 商品表 ----------
DROP TABLE IF EXISTS product_images;
DROP TABLE IF EXISTS products;
CREATE TABLE products (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 seller_id BIGINT NOT NULL COMMENT '卖家(商家)用户ID',
 shop_name VARCHAR(100) COMMENT '店铺名称（冗余字段）',
 name VARCHAR(150) NOT NULL COMMENT '商品名称',
 category VARCHAR(80) COMMENT '类别',
 original_price DECIMAL(12,2) COMMENT '原价',
 price DECIMAL(12,2) NOT NULL COMMENT '售价',
 size_info VARCHAR(100) COMMENT '尺寸大小',
 description TEXT COMMENT '使用说明',
 negotiable TINYINT DEFAULT 0 COMMENT '是否允许议价 0/1',
 stock INT NOT NULL DEFAULT 1 COMMENT '库存数量',
 sold_count INT NOT NULL DEFAULT 0 COMMENT '历史销量',
 condition_level VARCHAR(30) COMMENT '全新/九成新/八成新…',
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/REJECTED/OFF_SHELF/LOCKED',
 audit_reason VARCHAR(500),
 carousel_flag TINYINT DEFAULT 0 COMMENT '是否首页轮播 0/1',
 carousel_image VARCHAR(500) COMMENT '轮播图专用URL',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_product_seller(seller_id),
 INDEX idx_product_name(name),
 INDEX idx_product_status(status),
 CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS product_images;
CREATE TABLE product_images (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT NOT NULL,
 image_url VARCHAR(500) NOT NULL,
 sort_no INT DEFAULT 0,
 CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 购物车 ----------
DROP TABLE IF EXISTS carts;
CREATE TABLE carts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL DEFAULT 1,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_cart_user_product(user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 订单表 ----------
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_no VARCHAR(40) NOT NULL UNIQUE COMMENT '订单号',
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 product_name VARCHAR(150) NOT NULL,
 product_image VARCHAR(500) COMMENT '下单时商品封面(便于订单展示)',
 quantity INT NOT NULL,
 amount DECIMAL(12,2) NOT NULL COMMENT '实付金额(抵扣后)',
 original_amount DECIMAL(12,2) COMMENT '原价',
 points_deducted BIGINT DEFAULT 0 COMMENT '积分抵扣金额对应的积分数',
 status VARCHAR(30) NOT NULL COMMENT 'PAID_PENDING_RECEIVE / COMPLETED / RETURN_PENDING / RETURNED / CANCELLED',
 received_at DATETIME,
 completed_at DATETIME COMMENT '自动或手动确认收货时间',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_order_buyer(buyer_id),
 INDEX idx_order_seller(seller_id),
 INDEX idx_order_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 平台中间账户 ----------
DROP TABLE IF EXISTS platform_funds;
CREATE TABLE platform_funds (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_no VARCHAR(40) NOT NULL,
 seller_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL COMMENT '暂存金额',
 fee DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '平台手续费',
 fee_rate DECIMAL(8,5) DEFAULT 0,
 status VARCHAR(30) NOT NULL COMMENT 'HELD / SETTLED / REFUNDED',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 settled_at DATETIME,
 INDEX idx_fund_order(order_no),
 INDEX idx_fund_seller(seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 钱包流水 ----------
DROP TABLE IF EXISTS wallet_records;
CREATE TABLE wallet_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 order_no VARCHAR(40),
 type VARCHAR(30) NOT NULL COMMENT 'RECHARGE / PAY / REFUND / SETTLE / FEE',
 amount DECIMAL(12,2) NOT NULL COMMENT '正数=入账，负数=出账',
 balance_after DECIMAL(12,2) COMMENT '变动后余额',
 remark VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_wallet_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 积分流水 ----------
DROP TABLE IF EXISTS point_records;
CREATE TABLE point_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 order_no VARCHAR(40),
 type VARCHAR(30) NOT NULL COMMENT 'EARN / DEDUCT',
 points BIGINT NOT NULL COMMENT '正数',
 remark VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_point_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 退货表 ----------
DROP TABLE IF EXISTS returns;
CREATE TABLE returns (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 order_no VARCHAR(40) NOT NULL,
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED / REFUNDED',
 approved_at DATETIME,
 rejected_at DATETIME,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_return_order(order_id),
 INDEX idx_return_buyer(buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 评价表 ----------
DROP TABLE IF EXISTS reviews;
CREATE TABLE reviews (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 order_no VARCHAR(40) NOT NULL,
 reviewer_id BIGINT NOT NULL COMMENT '评价人',
 target_user_id BIGINT COMMENT '被评价人(对商家评价时填商家，对买家评价时填买家)',
 product_id BIGINT NOT NULL,
 rating INT NOT NULL COMMENT '1-5星',
 content VARCHAR(1000),
 review_type VARCHAR(30) NOT NULL COMMENT 'BUYER_TO_PRODUCT / BUYER_TO_SELLER / SELLER_TO_BUYER',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_review_product(product_id),
 INDEX idx_review_target(target_user_id),
 INDEX idx_review_order(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 拉黑表 ----------
DROP TABLE IF EXISTS blacklists;
CREATE TABLE blacklists (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 buyer_id BIGINT NOT NULL COMMENT '被拉黑的购买者',
 seller_id BIGINT COMMENT '操作的商家(平台拉黑时为null)',
 scope VARCHAR(20) NOT NULL COMMENT 'SELLER / SITE',
 reason VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_blacklist_buyer(buyer_id),
 INDEX idx_blacklist_seller(seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 用户操作日志 ----------
DROP TABLE IF EXISTS user_change_logs;
CREATE TABLE user_change_logs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 operator_id BIGINT,
 change_content VARCHAR(1000),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_log_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 议价 / 联系商家留言 ----------
DROP TABLE IF EXISTS negotiations;
CREATE TABLE negotiations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 offer_price DECIMAL(12,2) COMMENT '买家期望价格，可为空(纯留言)',
 message VARCHAR(1000) COMMENT '买家留言',
 reply VARCHAR(1000) COMMENT '商家回复',
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / REPLIED / ACCEPTED / REJECTED',
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 replied_at DATETIME,
 INDEX idx_nego_product(product_id),
 INDEX idx_nego_buyer(buyer_id),
 INDEX idx_nego_seller(seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 账号注销申请 ----------
DROP TABLE IF EXISTS account_cancellations;
CREATE TABLE account_cancellations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 reason VARCHAR(500),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
 admin_note VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME,
 INDEX idx_cancel_user(user_id),
 INDEX idx_cancel_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 验证码临时存储（可选，当前用内存Map暂存即可） ----------
-- 此处不建表，UtilityController 使用 ConcurrentHashMap 短时间存储验证码
