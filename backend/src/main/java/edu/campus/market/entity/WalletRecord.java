package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("wallet_records")
public class WalletRecord {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String orderNo;

    /** RECHARGE / PAY / REFUND / SETTLE / FEE */
    public String type;
    /** 正数入账，负数出账 */
    public BigDecimal amount;
    public BigDecimal balanceAfter;
    public String remark;

    public LocalDateTime createdAt;
}
