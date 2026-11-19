package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("blacklists")
public class Blacklist {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long buyerId;
    public Long sellerId;
    /** SELLER / SITE */
    public String scope;
    public String reason;

    public LocalDateTime createdAt;
}
