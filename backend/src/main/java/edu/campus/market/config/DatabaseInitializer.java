package edu.campus.market.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动补齐新增业务表，避免升级时必须手工执行 SQL。
 * 只做 CREATE TABLE IF NOT EXISTS，不会修改或删除已有数据。
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        create("negotiations", """
                CREATE TABLE IF NOT EXISTS negotiations (
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        create("account_cancellations", """
                CREATE TABLE IF NOT EXISTS account_cancellations (
                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                 user_id BIGINT NOT NULL,
                 reason VARCHAR(500),
                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
                 admin_note VARCHAR(500),
                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                 reviewed_at DATETIME,
                 INDEX idx_cancel_user(user_id),
                 INDEX idx_cancel_status(status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void create(String table, String ddl) {
        try {
            jdbc.execute(ddl);
        } catch (Exception e) {
            log.warn("自动创建表 {} 失败（不影响已有功能，可手动执行 DDL）：{}", table, e.getMessage());
        }
    }
}
