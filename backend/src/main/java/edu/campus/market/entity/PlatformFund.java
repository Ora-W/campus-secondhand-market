package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("platform_funds")
public class PlatformFund {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String orderNo;
    public Long sellerId;
    public Long buyerId;

    public BigDecimal amount;
    public BigDecimal fee;
    public BigDecimal feeRate;

    /** HELD / SETTLED / REFUNDED */
    public String status;

    public LocalDateTime createdAt;
    public LocalDateTime settledAt;
}
