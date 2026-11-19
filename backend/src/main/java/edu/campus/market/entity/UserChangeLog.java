package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("user_change_logs")
public class UserChangeLog {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long operatorId;
    public String changeContent;
    public LocalDateTime createdAt;
}
