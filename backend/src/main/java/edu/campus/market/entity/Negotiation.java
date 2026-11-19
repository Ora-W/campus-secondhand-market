package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 议价 / 联系商家留言 */
@TableName("negotiations")
public class Negotiation {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long productId;
    public Long buyerId;
    public Long sellerId;

    /** 买家期望价格，可为空（纯留言咨询） */
    public BigDecimal offerPrice;
    public String message;

    /** 商家回复 */
    public String reply;

    /** PENDING / REPLIED / ACCEPTED / REJECTED */
    public String status;

    public LocalDateTime createdAt;
    public LocalDateTime repliedAt;
}
