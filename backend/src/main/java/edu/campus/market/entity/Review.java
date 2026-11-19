package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("reviews")
public class Review {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long orderId;
    public String orderNo;

    public Long reviewerId;
    public Long targetUserId;
    public Long productId;

    /** 1~5 星 */
    public Integer rating;
    public String content;

    /** BUYER_TO_PRODUCT / BUYER_TO_SELLER / SELLER_TO_BUYER */
    public String reviewType;

    public LocalDateTime createdAt;
}
