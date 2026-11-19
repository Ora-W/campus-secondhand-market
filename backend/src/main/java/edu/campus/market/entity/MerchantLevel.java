package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;

@TableName("merchant_levels")
public class MerchantLevel {
    @TableId(type = IdType.INPUT)
    public Integer levelNo;
    public BigDecimal feeRate;
    public String description;
}
