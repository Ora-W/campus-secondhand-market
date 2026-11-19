package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sellerId;
    public String shopName;

    public String name;
    public String category;
    public BigDecimal originalPrice;
    public BigDecimal price;
    public String sizeInfo;
    public String description;
    /** 0 不允许议价  1 允许 */
    public Integer negotiable;
    public Integer stock;
    public Integer soldCount;
    /** 全新 / 九成新 / 八成新 / 七成新 / 有划痕 / 功能完好 */
    public String conditionLevel;
    /** PENDING / PUBLISHED / REJECTED / OFF_SHELF / LOCKED */
    public String status;
    public String auditReason;

    /** 首页轮播标记 */
    public Integer carouselFlag;
    public String carouselImage;

    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
