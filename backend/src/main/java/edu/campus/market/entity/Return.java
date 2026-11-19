package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("returns")
public class Return {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long orderId;
    public String orderNo;

    public Long buyerId;
    public Long sellerId;

    public String reason;
    /** PENDING / APPROVED / REJECTED / REFUNDED */
    public String status;

    public LocalDateTime approvedAt;
    public LocalDateTime rejectedAt;
    public LocalDateTime createdAt;
}
