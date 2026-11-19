package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String orderNo;

    public Long buyerId;
    public Long sellerId;
    public Long productId;
    public String productName;
    public String productImage;

    public Integer quantity;
    public BigDecimal amount;
    public BigDecimal originalAmount;
    public Long pointsDeducted;

    /** PAID_PENDING_RECEIVE / COMPLETED / RETURN_PENDING / RETURNED / CANCELLED */
    public String status;

    public LocalDateTime receivedAt;
    public LocalDateTime completedAt;

    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
