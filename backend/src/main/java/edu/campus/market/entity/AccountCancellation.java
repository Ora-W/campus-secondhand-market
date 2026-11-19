package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 账号注销申请，需管理员审核后才生效 */
@TableName("account_cancellations")
public class AccountCancellation {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String reason;
    /** PENDING / APPROVED / REJECTED */
    public String status;
    public String adminNote;
    public LocalDateTime createdAt;
    public LocalDateTime reviewedAt;
}
