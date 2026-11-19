package edu.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String username;
    public String password;
    public String realName;
    public String phone;
    public String email;
    public String city;
    public String gender;
    public String bankAccount;

    /** USER / MERCHANT / ADMIN */
    public String role;
    /** PERSONAL / MERCHANT / ADMIN */
    public String userType;
    /** PENDING / APPROVED / REJECTED */
    public String auditStatus;

    public String licenseImage;
    public String idCardImage;
    public String shopName;

    public BigDecimal walletBalance;
    public Long points;
    public Integer merchantLevel;
    /** 商家好评率，0~100 */
    public BigDecimal goodRate;

    /** ACTIVE / DISABLED */
    public String status;

    /** 限时封禁到期时间（空表示未封禁） */
    public LocalDateTime banUntil;
    public String banReason;

    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
