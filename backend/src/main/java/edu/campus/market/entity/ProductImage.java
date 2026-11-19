package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("product_images")
public class ProductImage {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long productId;
    public String imageUrl;
    public Integer sortNo;
}
