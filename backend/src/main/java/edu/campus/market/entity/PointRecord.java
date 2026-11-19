package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("point_records")
public class PointRecord {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String orderNo;

    /** EARN / DEDUCT */
    public String type;
    public Long points;
    public String remark;

    public LocalDateTime createdAt;
}
