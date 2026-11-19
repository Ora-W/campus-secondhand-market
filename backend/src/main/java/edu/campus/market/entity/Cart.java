package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("carts")
public class Cart {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long productId;
    public Integer quantity;
    public LocalDateTime createdAt;
}
