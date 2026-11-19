package edu.campus.market.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.campus.market.common.ApiResult;
import edu.campus.market.entity.*;
import edu.campus.market.mapper.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketService {

    private final UserMapper users;
    private final ProductMapper products;
    private final ProductImageMapper productImages;
    private final CartMapper carts;
    private final OrderMapper orders;
    private final PlatformFundMapper funds;
    private final WalletRecordMapper walletRecords;
    private final PointRecordMapper pointRecords;
    private final ReturnMapper returns;
    private final ReviewMapper reviews;
    private final BlacklistMapper blacklists;
    private final MerchantLevelMapper levels;
    private final UserChangeLogMapper logs;
    private final NegotiationMapper negotiations;
    private final AccountCancellationMapper cancellations;
    private final PasswordEncoder encoder;

    public MarketService(UserMapper u, ProductMapper p, ProductImageMapper pi,
                         CartMapper c, OrderMapper o, PlatformFundMapper f,
                         WalletRecordMapper wr, PointRecordMapper pr,
                         ReturnMapper r, ReviewMapper rv,
                         BlacklistMapper bl, MerchantLevelMapper lv,
                         UserChangeLogMapper lg, NegotiationMapper ng,
                         AccountCancellationMapper ac,
                         PasswordEncoder e) {
        this.users = u; this.products = p; this.productImages = pi;
        this.carts = c; this.orders = o; this.funds = f;
        this.walletRecords = wr; this.pointRecords = pr;
        this.returns = r; this.reviews = rv;
        this.blacklists = bl; this.levels = lv;
        this.logs = lg; this.negotiations = ng; this.cancellations = ac; this.encoder = e;
    }

    // ================= 工具 =================
    public User safe(User u) {
        if (u == null) return null;
        u.password = null;
        if (u.bankAccount != null && u.bankAccount.length() > 4) {
            int keep = 4;
            u.bankAccount = "*".repeat(u.bankAccount.length() - keep)
                    + u.bankAccount.substring(u.bankAccount.length() - keep);
        }
        return u;
    }

    /** 公开页面使用的用户视图：只保留店铺展示所需字段，避免泄露联系方式与证件 */
    public User publicUser(User u) {
        if (u == null) return null;
        User v = new User();
        v.id = u.id;
        v.username = u.username;
        v.realName = u.realName;
        v.shopName = u.shopName;
        v.role = u.role;
        v.userType = u.userType;
        v.goodRate = u.goodRate;
        v.merchantLevel = u.merchantLevel;
        v.createdAt = u.createdAt;
        return v;
    }

    private void logChange(Long userId, Long operatorId, String content) {
        UserChangeLog l = new UserChangeLog();
        l.userId = userId;
        l.operatorId = operatorId;
        l.changeContent = content;
        logs.insert(l);
    }

    private void saveWalletRecord(Long userId, String type, BigDecimal amount, String orderNo, String remark) {
        User u = users.selectById(userId);
        WalletRecord wr = new WalletRecord();
        wr.userId = userId;
        wr.type = type;
        wr.amount = amount;
        wr.orderNo = orderNo;
        wr.remark = remark;
        wr.balanceAfter = u.walletBalance;
        walletRecords.insert(wr);
    }

    private void savePointRecord(Long userId, String type, long points, String orderNo, String remark) {
        PointRecord pr = new PointRecord();
        pr.userId = userId;
        pr.type = type;
        pr.points = points;
        pr.orderNo = orderNo;
        pr.remark = remark;
        pointRecords.insert(pr);
    }

    // ================= 注册登录 =================

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return (t.isEmpty() || "null".equalsIgnoreCase(t)) ? null : t;
    }

    /** 注册（图片验证码由 Controller 层校验，此处不再处理字符串字段中是否包含验证码） */
    @Transactional
    public User register(Map<String, Object> m) {
        String username = Objects.toString(m.get("username"), "").trim();
        if (username.isEmpty()) throw new IllegalArgumentException("请填写登录账号");
        String password = Objects.toString(m.get("password"), "");
        if (password.isBlank()) throw new IllegalArgumentException("请设置登录密码");
        if (password.length() < 6) throw new IllegalArgumentException("密码长度不能少于 6 位");
        if (users.selectCount(new QueryWrapper<User>().eq("username", username)) > 0)
            throw new IllegalArgumentException("用户名已存在");

        String userType = Objects.toString(m.getOrDefault("userType", "PERSONAL"), "PERSONAL").toUpperCase();
        User u = new User();
        u.username = username;
        u.password = encoder.encode(password);
        u.realName = blankToNull(Objects.toString(m.get("realName"), null));
        u.phone = blankToNull(Objects.toString(m.get("phone"), null));
        u.email = blankToNull(Objects.toString(m.get("email"), null));
        u.city = blankToNull(Objects.toString(m.get("city"), null));
        u.gender = blankToNull(Objects.toString(m.get("gender"), null));
        String bank = Objects.toString(m.get("bankAccount"), "").trim();
        u.bankAccount = bank.isEmpty() ? null : bank;

        // 注册资料全部必填
        if (u.realName == null) throw new IllegalArgumentException("请填写真实姓名");
        if (u.phone == null) throw new IllegalArgumentException("请填写手机号");
        if (!u.phone.matches("1\\d{10}")) throw new IllegalArgumentException("手机号格式不正确，应为 11 位数字");
        if (u.email == null) throw new IllegalArgumentException("请填写邮箱");
        if (!u.email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) throw new IllegalArgumentException("邮箱格式不正确");
        if (u.city == null) throw new IllegalArgumentException("请填写所在城市");
        if (u.gender == null) throw new IllegalArgumentException("请选择性别");
        if (u.bankAccount == null) throw new IllegalArgumentException("请填写 16 位银行卡号");
        if (!u.bankAccount.matches("\\d{16}")) throw new IllegalArgumentException("银行账号必须为16位数字");

        if ("MERCHANT".equals(userType)) {
            u.role = "MERCHANT";
            u.userType = "MERCHANT";
            String shop = blankToNull(Objects.toString(m.get("shopName"), null));
            if (shop == null) throw new IllegalArgumentException("商家请填写店铺名称");
            u.shopName = shop;
            u.licenseImage = blankToNull(Objects.toString(m.get("licenseImage"), null));
            u.idCardImage = blankToNull(Objects.toString(m.get("idCardImage"), null));
            if (u.licenseImage == null) throw new IllegalArgumentException("商家请上传营业执照图片");
            if (u.idCardImage == null) throw new IllegalArgumentException("商家请上传身份证图片");
            u.merchantLevel = 1;
        } else {
            u.role = "USER";
            u.userType = "PERSONAL";
            u.merchantLevel = null;
        }

        u.auditStatus = "PENDING";
        u.status = "ACTIVE";
        u.walletBalance = BigDecimal.ZERO;
        u.points = 0L;
        u.goodRate = new BigDecimal("100");
        u.banUntil = null;
        u.banReason = null;

        users.insert(u);
        logChange(u.id, null, "用户注册");
        return safe(u);
    }

    public Map<String, Object> login(String username, String password) {
        User u = users.selectOne(new QueryWrapper<User>().eq("username", username));
        if (u == null || !encoder.matches(password, u.password))
            throw new IllegalArgumentException("用户名或密码错误");
        if (!"APPROVED".equals(u.auditStatus) && !"ADMIN".equals(u.role)) {
            if ("REJECTED".equals(u.auditStatus))
                throw new IllegalArgumentException("您的注册申请未通过管理员审核，无法登录");
            throw new IllegalArgumentException("您的注册申请正在等待管理员审核，暂时无法登录");
        }
        if (!"ACTIVE".equals(u.status))
            throw new IllegalArgumentException("账号已被停用，无法登录");
        // 封禁到期自动解封
        if (u.banUntil != null && u.banUntil.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("账号已被封禁，到期时间：" + u.banUntil);

        return Map.of("user", safe(u), "token", u.id + ":" + u.role);
    }

    // ================= 管理员：用户审核/管理 =================

    public List<User> pendingUsers() {
        return users.selectList(new QueryWrapper<User>().eq("audit_status", "PENDING"));
    }

    @Transactional
    public void auditUser(Long id, boolean approved, Long operatorId) {
        User u = users.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        u.auditStatus = approved ? "APPROVED" : "REJECTED";
        users.updateById(u);
        logChange(id, operatorId, approved ? "审核通过" : "审核拒绝");
    }

    public List<User> allUsers(String keyword) {
        QueryWrapper<User> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("username", keyword)
                    .or().like("real_name", keyword)
                    .or().like("phone", keyword));
        }
        q.orderByDesc("id");
        return users.selectList(q).stream().map(this::safe).collect(Collectors.toList());
    }

    /** 管理员查看用户完整注册资料（含营业执照、身份证、完整银行卡号） */
    public Map<String, Object> adminUserDetail(Long id) {
        User u = users.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        u.password = null;   // 仅脱敏密码，其余管理员可见
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u);
        m.put("serviceReviews", sellerServiceReviews(id));
        m.put("buyerReviews", reviews.selectList(new QueryWrapper<Review>()
                .eq("target_user_id", id).eq("review_type", "SELLER_TO_BUYER")
                .orderByDesc("created_at")));
        m.put("orderCount", orders.selectCount(new QueryWrapper<Order>().eq("seller_id", id)));
        return m;
    }

    /** 管理员：商家列表（含好评率、服务评价数、订单数），用于动态调整等级 */
    public List<Map<String, Object>> adminMerchants() {
        List<User> list = users.selectList(new QueryWrapper<User>()
                .eq("role", "MERCHANT").orderByDesc("id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (User m : list) {
            List<Review> srv = sellerServiceReviews(m.id);
            double avg = srv.stream().mapToInt(r -> r.rating).average().orElse(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchant", safe(m));
            row.put("goodRate", m.goodRate);
            row.put("serviceReviewCount", srv.size());
            row.put("serviceAvg", Math.round(avg * 100.0) / 100.0);
            row.put("orderCount", orders.selectCount(new QueryWrapper<Order>().eq("seller_id", m.id)));
            row.put("productCount", products.selectCount(new QueryWrapper<Product>().eq("seller_id", m.id)));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void updateUser(Long id, Map<String, Object> body, Long operatorId) {
        User u = users.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        if (body.containsKey("realName")) u.realName = Objects.toString(body.get("realName"), null);
        if (body.containsKey("phone")) u.phone = Objects.toString(body.get("phone"), null);
        if (body.containsKey("email")) u.email = Objects.toString(body.get("email"), null);
        if (body.containsKey("city")) u.city = Objects.toString(body.get("city"), null);
        if (body.containsKey("gender")) u.gender = Objects.toString(body.get("gender"), null);
        if (body.containsKey("shopName")) u.shopName = Objects.toString(body.get("shopName"), null);
        if (body.containsKey("status")) {
            String s = Objects.toString(body.get("status"));
            if (!"ACTIVE".equals(s) && !"DISABLED".equals(s)) throw new IllegalArgumentException("status 只能是 ACTIVE/DISABLED");
            u.status = s;
        }
        users.updateById(u);
        logChange(id, operatorId, "管理员修改用户资料");
    }

    @Transactional
    public void deleteUser(Long id, Long operatorId) {
        User u = users.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        if ("ADMIN".equals(u.role)) throw new IllegalArgumentException("管理员账号不可删除");
        users.deleteById(id);
        logChange(id, operatorId, "管理员删除用户");
    }

    /** 管理员充值（增加钱包余额），同时写入钱包流水 */
    @Transactional
    public void recharge(Long id, BigDecimal amount, Long operatorId) {
        User u = users.selectById(id);
        if (u == null || amount.signum() <= 0) throw new IllegalArgumentException("用户或金额不正确");
        if (!"APPROVED".equals(u.auditStatus))
            throw new IllegalArgumentException("该用户尚未通过注册审核，不能充值");
        u.walletBalance = u.walletBalance.add(amount);
        users.updateById(u);
        saveWalletRecord(id, "RECHARGE", amount, null, "管理员充值 by " + operatorId);
        logChange(id, operatorId, "充值 " + amount);
    }

    // ================= 管理员：商家等级 =================

    public List<MerchantLevel> allMerchantLevels() {
        return levels.selectList(null);
    }

    /** 管理员给商家设置等级 */
    @Transactional
    public void setMerchantLevel(Long merchantId, int level, Long operatorId) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("等级必须在 1~5 之间");
        User u = users.selectById(merchantId);
        if (u == null || !"MERCHANT".equals(u.role)) throw new IllegalArgumentException("商家不存在");
        u.merchantLevel = level;
        users.updateById(u);
        logChange(merchantId, operatorId, "设置商家等级为 " + level);
    }

    // ================= 管理员：封禁（限时 / 永久） =================

    /** 校验商家当前是否可经营（被封禁 / 店铺被关闭时禁止操作） */
    private void checkMerchantActive(User seller) {
        if (seller == null) throw new IllegalArgumentException("商家不存在");
        if (!"ACTIVE".equals(seller.status))
            throw new IllegalArgumentException("店铺已被平台暂时关闭，无法进行该操作");
        if (seller.banUntil != null && seller.banUntil.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("您已被平台封禁至 " + seller.banUntil + "，无法进行该操作");
    }

    // ================= 管理员：限时封禁（不允许发布商品） =================

    /** 限时封禁，until 为到期时间，null 表示永久（用一个非常远的时间表示） */
    @Transactional
    public void banUser(Long userId, LocalDateTime until, String reason, Long operatorId) {
        User u = users.selectById(userId);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        u.banUntil = until != null ? until : LocalDateTime.now().plusYears(10);
        u.banReason = reason;
        users.updateById(u);
        // 若为商家：自动下架全部未下架的商品
        if ("MERCHANT".equals(u.role)) {
            List<Product> list = products.selectList(new QueryWrapper<Product>()
                    .eq("seller_id", userId).eq("status", "PUBLISHED"));
            for (Product p : list) {
                p.status = "OFF_SHELF";
                p.auditReason = "商家被封禁自动下架";
                products.updateById(p);
            }
        }
        logChange(userId, operatorId, "封禁：" + reason + "，到期：" + u.banUntil);
    }

    /** 管理员关闭店铺：全部商品下架，店铺暂时关闭（商家无法登录经营） */
    @Transactional
    public void closeShop(Long merchantId, String reason, Long operatorId) {
        User m = users.selectById(merchantId);
        if (m == null || !"MERCHANT".equals(m.role)) throw new IllegalArgumentException("商家不存在");
        m.status = "DISABLED";
        users.updateById(m);
        List<Product> list = products.selectList(new QueryWrapper<Product>()
                .eq("seller_id", merchantId).in("status", Arrays.asList("PUBLISHED", "PENDING")));
        for (Product p : list) {
            p.status = "OFF_SHELF";
            p.auditReason = "店铺被平台暂时关闭" + (reason == null || reason.isBlank() ? "" : "：" + reason);
            products.updateById(p);
        }
        logChange(merchantId, operatorId, "店铺被暂时关闭：" + reason);
    }

    /** 管理员恢复店铺 */
    @Transactional
    public void openShop(Long merchantId, Long operatorId) {
        User m = users.selectById(merchantId);
        if (m == null || !"MERCHANT".equals(m.role)) throw new IllegalArgumentException("商家不存在");
        m.status = "ACTIVE";
        users.updateById(m);
        logChange(merchantId, operatorId, "店铺恢复营业");
    }


    @Transactional
    public void unbanUser(Long userId, Long operatorId) {
        User u = users.selectById(userId);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        // MyBatis-Plus 默认忽略 null 字段，必须用 UpdateWrapper 才能把封禁字段清空
        users.update(null, new UpdateWrapper<User>().eq("id", userId)
                .set("ban_until", null).set("ban_reason", null));
        logChange(userId, operatorId, "解除封禁");
    }

    // ================= 管理员：拉黑恶意购买者 =================

    @Transactional
    public void addBlacklist(Long buyerId, Long sellerId, String scope, String reason, Long operatorId) {
        User b = users.selectById(buyerId);
        if (b == null) throw new IllegalArgumentException("购买者不存在");
        Blacklist bl = new Blacklist();
        bl.buyerId = buyerId;
        bl.sellerId = sellerId;   // SELLER 级时为商家ID；SITE 级时为 null
        bl.scope = scope;
        bl.reason = reason;
        blacklists.insert(bl);
        logChange(buyerId, operatorId, "被" + ("SITE".equals(scope) ? "平台" : "商家") + "拉黑：" + reason);
    }

    @Transactional
    public void removeBlacklist(Long blacklistId, Long operatorId) {
        Blacklist bl = blacklists.selectById(blacklistId);
        if (bl == null) throw new IllegalArgumentException("记录不存在");
        blacklists.deleteById(blacklistId);
        logChange(bl.buyerId, operatorId, "移出黑名单");
    }

    /** 平台级黑名单列表（管理员） */
    public List<Map<String, Object>> adminBlacklists() {
        List<Blacklist> list = blacklists.selectList(new QueryWrapper<Blacklist>().orderByDesc("created_at"));
        return enrichBlacklists(list);
    }

    /** 某商家自己拉黑的买家 */
    public List<Map<String, Object>> merchantBlacklists(Long sellerId) {
        List<Blacklist> list = blacklists.selectList(new QueryWrapper<Blacklist>()
                .eq("seller_id", sellerId).eq("scope", "SELLER").orderByDesc("created_at"));
        return enrichBlacklists(list);
    }

    /** 商家移除自己拉黑的买家 */
    @Transactional
    public void removeMerchantBlacklist(Long id, Long sellerId, Long operatorId) {
        Blacklist bl = blacklists.selectById(id);
        if (bl == null || !Objects.equals(bl.sellerId, sellerId) || !"SELLER".equals(bl.scope))
            throw new IllegalArgumentException("记录不存在或无权操作");
        blacklists.deleteById(id);
        logChange(bl.buyerId, operatorId, "被商家移出黑名单");
    }

    private List<Map<String, Object>> enrichBlacklists(List<Blacklist> list) {
        if (list.isEmpty()) return Collections.emptyList();
        Set<Long> ids = list.stream().map(b -> b.buyerId).collect(Collectors.toSet());
        Map<Long, User> userMap = users.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(u -> u.id, this::safe));
        return list.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("blacklist", b);
            m.put("buyer", userMap.get(b.buyerId));
            return m;
        }).collect(Collectors.toList());
    }

    // ================= 账号注销（需管理员审核） =================

    /** 用户提交注销申请 */
    @Transactional
    public void requestCancellation(Long userId, String reason) {
        User u = users.selectById(userId);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        if ("ADMIN".equals(u.role)) throw new IllegalArgumentException("管理员账号不能注销");
        long pending = cancellations.selectCount(new QueryWrapper<AccountCancellation>()
                .eq("user_id", userId).eq("status", "PENDING"));
        if (pending > 0) throw new IllegalArgumentException("您已提交过注销申请，请等待管理员审核");
        AccountCancellation a = new AccountCancellation();
        a.userId = userId;
        a.reason = reason;
        a.status = "PENDING";
        cancellations.insert(a);
        logChange(userId, null, "提交账号注销申请");
    }

    /** 查看自己的最新注销申请状态 */
    public AccountCancellation myCancellation(Long userId) {
        return cancellations.selectOne(new QueryWrapper<AccountCancellation>()
                .eq("user_id", userId).orderByDesc("id").last("LIMIT 1"));
    }

    /** 管理员查看注销申请列表 */
    public List<Map<String, Object>> adminCancellations() {
        List<AccountCancellation> list = cancellations.selectList(new QueryWrapper<AccountCancellation>()
                .orderByDesc("created_at"));
        if (list.isEmpty()) return Collections.emptyList();
        Set<Long> ids = list.stream().map(a -> a.userId).collect(Collectors.toSet());
        Map<Long, User> um = users.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(x -> x.id, this::safe));
        return list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("request", a);
            m.put("user", um.get(a.userId));
            return m;
        }).collect(Collectors.toList());
    }

    /** 管理员审核注销申请；通过后账号停用、商家商品全部下架 */
    @Transactional
    public void reviewCancellation(Long id, boolean approved, String note, Long operatorId) {
        AccountCancellation a = cancellations.selectById(id);
        if (a == null) throw new IllegalArgumentException("申请不存在");
        if (!"PENDING".equals(a.status)) throw new IllegalArgumentException("该申请已处理");
        a.status = approved ? "APPROVED" : "REJECTED";
        a.adminNote = note;
        a.reviewedAt = LocalDateTime.now();
        cancellations.updateById(a);
        if (approved) {
            User u = users.selectById(a.userId);
            if (u != null) {
                u.status = "DISABLED";
                users.updateById(u);
                if ("MERCHANT".equals(u.role)) {
                    List<Product> list = products.selectList(new QueryWrapper<Product>()
                            .eq("seller_id", u.id).in("status", Arrays.asList("PUBLISHED", "PENDING")));
                    for (Product p : list) {
                        p.status = "OFF_SHELF";
                        p.auditReason = "账号已注销，商品自动下架";
                        products.updateById(p);
                    }
                }
            }
        }
        logChange(a.userId, operatorId,
                approved ? "账号注销申请已通过" : "账号注销申请被拒绝：" + (note == null ? "" : note));
    }

    // ================= 顶部消息 / 待办角标 =================

    /** 当前登录用户的消息与待办汇总，用于导航红点与明显提示 */
    public Map<String, Object> notificationSummary(Long userId, String role, Long seenNegoId, Long seenReturnId) {
        Map<String, Object> m = new LinkedHashMap<>();
        User me = users.selectById(userId);
        if (me != null) {
            m.put("banned", me.banUntil != null && me.banUntil.isAfter(LocalDateTime.now()));
            m.put("banUntil", me.banUntil);
            m.put("banReason", me.banReason);
            m.put("shopClosed", "MERCHANT".equals(me.role) && "DISABLED".equals(me.status));
        }
        if ("ADMIN".equals(role)) {
            long pu = users.selectCount(new QueryWrapper<User>().eq("audit_status", "PENDING"));
            long pp = products.selectCount(new QueryWrapper<Product>().eq("status", "PENDING"));
            long pc = cancellations.selectCount(new QueryWrapper<AccountCancellation>().eq("status", "PENDING"));
            m.put("pendingUsers", pu);
            m.put("pendingProducts", pp);
            m.put("pendingCancellations", pc);
            m.put("total", pu + pp + pc);
            m.put("hint", pu + pp + pc > 0
                    ? "待审核：用户 " + pu + " / 商品 " + pp + " / 注销 " + pc : "");
        } else if ("MERCHANT".equals(role)) {
            long nego = negotiations.selectCount(new QueryWrapper<Negotiation>()
                    .eq("seller_id", userId).eq("status", "PENDING"));
            long ret = returns.selectCount(new QueryWrapper<Return>()
                    .eq("seller_id", userId).eq("status", "PENDING"));
            long paid = orders.selectCount(new QueryWrapper<Order>()
                    .eq("seller_id", userId).eq("status", "PAID_PENDING_RECEIVE"));
            m.put("negotiations", nego);
            m.put("returns", ret);
            m.put("paidOrders", paid);
            m.put("total", nego + ret + paid);
            Negotiation last = negotiations.selectOne(new QueryWrapper<Negotiation>()
                    .eq("seller_id", userId).orderByDesc("id").last("LIMIT 1"));
            m.put("latestId", last == null ? 0 : last.id);
            m.put("hint", nego + ret + paid > 0
                    ? "待处理：议价 " + nego + " / 退货 " + ret + " / 待发货订单 " + paid : "");
        } else {
            long replied = negotiations.selectCount(new QueryWrapper<Negotiation>()
                    .eq("buyer_id", userId).in("status", Arrays.asList("REPLIED", "ACCEPTED"))
                    .gt("id", seenNegoId == null ? 0 : seenNegoId));
            long receive = orders.selectCount(new QueryWrapper<Order>()
                    .eq("buyer_id", userId).in("status", Arrays.asList("PAID_PENDING_RECEIVE", "SHIPPED")));
            long refunded = returns.selectCount(new QueryWrapper<Return>()
                    .eq("buyer_id", userId).in("status", Arrays.asList("APPROVED", "REJECTED", "REFUNDED"))
                    .gt("id", seenReturnId == null ? 0 : seenReturnId));
            m.put("negotiationReplies", replied);
            m.put("pendingReceive", receive);
            m.put("returnResults", refunded);
            m.put("total", replied + receive + refunded);
            Negotiation last = negotiations.selectOne(new QueryWrapper<Negotiation>()
                    .eq("buyer_id", userId).orderByDesc("id").last("LIMIT 1"));
            m.put("latestId", last == null ? 0 : last.id);
            Return lastRet = returns.selectOne(new QueryWrapper<Return>()
                    .eq("buyer_id", userId).orderByDesc("id").last("LIMIT 1"));
            m.put("latestReturnId", lastRet == null ? 0 : lastRet.id);
            m.put("hint", replied + receive + refunded > 0
                    ? "有 " + replied + " 条议价回复 / " + receive + " 笔待收货订单" : "");
            AccountCancellation ca = myCancellation(userId);
            m.put("cancelStatus", ca == null ? null : ca.status);
            m.put("cancelNote", ca == null ? null : ca.adminNote);
        }
        return m;
    }

    /** 校验买家是否被禁止购买 */
    private void checkBlacklist(Long buyerId, Long sellerId) {
        Long siteCount = blacklists.selectCount(new QueryWrapper<Blacklist>()
                .eq("buyer_id", buyerId).eq("scope", "SITE"));
        if (siteCount > 0) throw new IllegalArgumentException("您已被平台拉黑，无法购买任何商品");
        Long sellerCount = blacklists.selectCount(new QueryWrapper<Blacklist>()
                .eq("buyer_id", buyerId).eq("seller_id", sellerId).eq("scope", "SELLER"));
        if (sellerCount > 0) throw new IllegalArgumentException("您已被该商家拉黑，无法购买其商品");
    }

    // ================= 商品：搜索 / 列表 =================

    /** 前台商品搜索。sort: price_asc / price_desc / sales / rating（好评率） / default */
    public List<Map<String, Object>> searchProducts(String keyword, String sort) {
        QueryWrapper<Product> q = new QueryWrapper<Product>().eq("status", "PUBLISHED");
        if (keyword != null && !keyword.isBlank()) q.like("name", keyword);
        if ("price_asc".equals(sort)) q.orderByAsc("price");
        else if ("price_desc".equals(sort)) q.orderByDesc("price");
        else if ("sales".equals(sort)) q.orderByDesc("sold_count");
        else q.orderByDesc("created_at");
        List<Product> list = products.selectList(q);
        List<Map<String, Object>> rows = enrichProducts(list);
        // 好评率排序：按商家好评率降序，同分保持发布时间倒序
        if ("rating".equals(sort)) {
            rows.sort((a, b) -> {
                User sa = (User) a.get("seller");
                User sb = (User) b.get("seller");
                BigDecimal ra = sa != null && sa.goodRate != null ? sa.goodRate : BigDecimal.ZERO;
                BigDecimal rb = sb != null && sb.goodRate != null ? sb.goodRate : BigDecimal.ZERO;
                return rb.compareTo(ra);
            });
        }
        return rows;
    }

    /** 首页轮播图商品：每个商家最多取 1 件，保证推荐位覆盖不同商家 */
    public List<Map<String, Object>> carouselProducts() {
        QueryWrapper<Product> q = new QueryWrapper<Product>()
                .eq("status", "PUBLISHED").eq("carousel_flag", 1)
                .orderByDesc("updated_at");
        List<Product> all = products.selectList(q);
        List<Product> picked = new ArrayList<>(all);
        // 轮播位不足 4 个时，用最新在售商品补齐（允许多件来自同一商家），保证有多张图可以轮播
        Set<Long> pickedIds = picked.stream().map(p -> p.id).collect(Collectors.toSet());
        if (picked.size() < 4) {
            List<Product> latest = products.selectList(new QueryWrapper<Product>()
                    .eq("status", "PUBLISHED").orderByDesc("created_at").last("LIMIT 40"));
            for (Product p : latest) {
                if (picked.size() >= 4) break;
                if (pickedIds.add(p.id)) picked.add(p);
            }
        }
        return enrichProducts(picked);
    }

    /** 管理员：按状态查看商品（空=全部），用于轮播推荐与强制下架 */
    public List<Map<String, Object>> adminProducts(String status) {
        QueryWrapper<Product> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status);
        q.orderByDesc("created_at").last("LIMIT 200");
        return enrichProducts(products.selectList(q));
    }

    /** 管理员强制下架某个商品 */
    @Transactional
    public void adminTakeDown(Long productId, String reason) {
        Product p = products.selectById(productId);
        if (p == null) throw new IllegalArgumentException("商品不存在");
        p.status = "OFF_SHELF";
        p.carouselFlag = 0;
        p.auditReason = "管理员强制下架" + (reason == null || reason.isBlank() ? "" : "：" + reason);
        products.updateById(p);
    }

    /** 商品详情（带图片列表、商家信息） */
    public Map<String, Object> productDetail(Long productId) {
        Product p = products.selectById(productId);
        if (p == null || !"PUBLISHED".equals(p.status)) throw new IllegalArgumentException("商品不存在或已下架");
        List<ProductImage> imgs = productImages.selectList(new QueryWrapper<ProductImage>()
                .eq("product_id", productId).orderByAsc("sort_no"));
        User seller = users.selectById(p.sellerId);
        // 商品评价数、平均星
        List<Review> reviewsList = reviews.selectList(new QueryWrapper<Review>()
                .eq("product_id", productId).eq("review_type", "BUYER_TO_PRODUCT"));
        double avg = reviewsList.stream().mapToInt(r -> r.rating).average().orElse(0);
        long count = reviewsList.size();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("product", p);
        map.put("images", imgs);
        map.put("seller", publicUser(seller));
        map.put("reviewAvg", Math.round(avg * 100.0) / 100.0);
        map.put("reviewCount", count);
        return map;
    }

    private List<Map<String, Object>> enrichProducts(List<Product> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        // 批量加载图片(取第一张作为封面) 和 seller
        Set<Long> ids = list.stream().map(p -> p.sellerId).collect(Collectors.toSet());
        Map<Long, User> sellerMap = users.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(u -> u.id, this::publicUser));
        Map<Long, String> coverMap = new HashMap<>();
        for (Product p : list) {
            ProductImage pi = productImages.selectOne(new QueryWrapper<ProductImage>()
                    .eq("product_id", p.id).orderByAsc("sort_no").last("LIMIT 1"));
            coverMap.put(p.id, pi != null ? pi.imageUrl : null);
        }
        return list.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("product", p);
            m.put("seller", sellerMap.get(p.sellerId));
            m.put("coverImage", coverMap.get(p.id));
            return m;
        }).collect(Collectors.toList());
    }

    // ================= 商品：商家发布 / 审核 / 下架 =================

    /** 商家发布商品（待审核） */
    @Transactional
    public Product createProduct(Long sellerId, Map<String, Object> m) {
        User seller = users.selectById(sellerId);
        checkMerchantActive(seller);

        Product p = new Product();
        p.sellerId = sellerId;
        p.shopName = seller.shopName != null ? seller.shopName : (seller.realName + "的小店");
        p.name = Objects.toString(m.get("name"));
        p.category = Objects.toString(m.get("category"), null);
        p.description = Objects.toString(m.get("description"), null);
        p.sizeInfo = Objects.toString(m.get("sizeInfo"), null);
        p.conditionLevel = Objects.toString(m.get("conditionLevel"), null);
        p.originalPrice = new BigDecimal(Objects.toString(m.getOrDefault("originalPrice", "0")));
        p.price = new BigDecimal(Objects.toString(m.getOrDefault("price", "0")));
        p.stock = Integer.parseInt(Objects.toString(m.getOrDefault("stock", "1")));
        if (p.price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("商品价格必须大于 0");
        if (p.originalPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("商品原价不能为负数");
        if (p.stock < 0) throw new IllegalArgumentException("商品库存不能为负数");
        p.negotiable = Boolean.parseBoolean(Objects.toString(m.getOrDefault("negotiable", "false"))) ? 1 : 0;
        p.soldCount = 0;
        p.status = "PENDING";
        p.carouselFlag = 0;
        products.insert(p);

        // 多张图片
        Object imgs = m.get("images");
        if (imgs instanceof List<?> list) {
            int i = 0;
            for (Object url : list) {
                ProductImage pi = new ProductImage();
                pi.productId = p.id;
                pi.imageUrl = Objects.toString(url);
                pi.sortNo = i++;
                productImages.insert(pi);
            }
        } else if (imgs instanceof String s && !s.isBlank()) {
            ProductImage pi = new ProductImage();
            pi.productId = p.id;
            pi.imageUrl = s;
            pi.sortNo = 0;
            productImages.insert(pi);
        }
        return p;
    }

    /** 管理员待审核商品列表（带封面图 + 卖家信息） */
    public List<Map<String, Object>> pendingProducts() {
        List<Product> list = products.selectList(new QueryWrapper<Product>()
                .eq("status", "PENDING").orderByDesc("created_at"));
        return enrichProducts(list);
    }

    @Transactional
    public void auditProduct(Long id, boolean approved, String reason) {
        Product p = products.selectById(id);
        if (p == null) throw new IllegalArgumentException("商品不存在");
        p.status = approved ? "PUBLISHED" : "REJECTED";
        p.auditReason = reason;
        products.updateById(p);
    }

    @Transactional
    public void takeDown(Long id, Long sellerId) {
        Product p = products.selectById(id);
        if (p == null || !Objects.equals(p.sellerId, sellerId))
            throw new IllegalArgumentException("商品不存在或无权操作");
        if (!"PUBLISHED".equals(p.status))
            throw new IllegalArgumentException("商品当前状态不允许下架");
        p.status = "OFF_SHELF";
        products.updateById(p);
    }

    /** 商家编辑商品：保存后必须重新提交管理员审核（状态回到 PENDING） */
    @Transactional
    public Product updateProduct(Long productId, Long sellerId, Map<String, Object> m) {
        Product p = products.selectById(productId);
        if (p == null || !Objects.equals(p.sellerId, sellerId))
            throw new IllegalArgumentException("商品不存在或无权操作");
        checkMerchantActive(users.selectById(sellerId));
        if ("LOCKED".equals(p.status))
            throw new IllegalArgumentException("商品已售罄锁定，无法编辑");

        if (m.containsKey("name")) p.name = Objects.toString(m.get("name"));
        if (m.containsKey("category")) p.category = Objects.toString(m.get("category"), null);
        if (m.containsKey("description")) p.description = Objects.toString(m.get("description"), null);
        if (m.containsKey("sizeInfo")) p.sizeInfo = Objects.toString(m.get("sizeInfo"), null);
        if (m.containsKey("conditionLevel")) p.conditionLevel = Objects.toString(m.get("conditionLevel"), null);
        if (m.containsKey("originalPrice")) p.originalPrice = new BigDecimal(Objects.toString(m.get("originalPrice")));
        if (m.containsKey("price")) p.price = new BigDecimal(Objects.toString(m.get("price")));
        if (m.containsKey("stock")) p.stock = Integer.parseInt(Objects.toString(m.get("stock")));
        if (p.price == null || p.price.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("商品价格必须大于 0");
        if (p.originalPrice != null && p.originalPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("商品原价不能为负数");
        if (p.stock != null && p.stock < 0)
            throw new IllegalArgumentException("商品库存不能为负数");
        if (m.containsKey("negotiable"))
            p.negotiable = Boolean.parseBoolean(Objects.toString(m.get("negotiable"))) || "1".equals(Objects.toString(m.get("negotiable"))) ? 1 : 0;

        // 图片整体替换
        Object imgs = m.get("images");
        if (imgs instanceof List<?> list) {
            productImages.delete(new QueryWrapper<ProductImage>().eq("product_id", productId));
            int i = 0;
            for (Object url : list) {
                ProductImage pi = new ProductImage();
                pi.productId = productId;
                pi.imageUrl = Objects.toString(url);
                pi.sortNo = i++;
                productImages.insert(pi);
            }
        }

        // 修改后重新送审
        p.status = "PENDING";
        p.auditReason = null;
        products.updateById(p);
        return p;
    }

    /** 商家重新上架：同样需要管理员重新审核 */
    @Transactional
    public void republish(Long productId, Long sellerId) {
        Product p = products.selectById(productId);
        if (p == null || !Objects.equals(p.sellerId, sellerId))
            throw new IllegalArgumentException("商品不存在或无权操作");
        checkMerchantActive(users.selectById(sellerId));
        if (!"OFF_SHELF".equals(p.status) && !"REJECTED".equals(p.status))
            throw new IllegalArgumentException("只有已下架或被驳回的商品可以重新上架");
        if (p.stock <= 0)
            throw new IllegalArgumentException("商品库存为 0，请先编辑补充库存");
        p.status = "PENDING";
        p.auditReason = null;
        products.updateById(p);
    }

    /** 管理员设置/取消首页轮播 */
    @Transactional
    public void toggleCarousel(Long productId, boolean on) {
        Product p = products.selectById(productId);
        if (p == null) throw new IllegalArgumentException("商品不存在");
        p.carouselFlag = on ? 1 : 0;
        if (on && Objects.toString(p.carouselImage, "").isBlank()) {
            ProductImage pi = productImages.selectOne(new QueryWrapper<ProductImage>()
                    .eq("product_id", productId).orderByAsc("sort_no").last("LIMIT 1"));
            p.carouselImage = pi != null ? pi.imageUrl : null;
        }
        products.updateById(p);
    }

    // ================= 商家店铺维度 =================

    /** 商家自己的店铺信息 */
    public Map<String, Object> merchantShop(Long merchantId) {
        User m = users.selectById(merchantId);
        if (m == null || !"MERCHANT".equals(m.role)) throw new IllegalArgumentException("商家不存在");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("merchant", safe(m));
        // 各状态商品数
        map.put("publishedCount", products.selectCount(new QueryWrapper<Product>()
                .eq("seller_id", merchantId).eq("status", "PUBLISHED")));
        map.put("offShelfCount", products.selectCount(new QueryWrapper<Product>()
                .eq("seller_id", merchantId).eq("status", "OFF_SHELF")));
        map.put("pendingCount", products.selectCount(new QueryWrapper<Product>()
                .eq("seller_id", merchantId).eq("status", "PENDING")));
        long sold = products.selectList(new QueryWrapper<Product>().eq("seller_id", merchantId))
                .stream().mapToLong(p -> p.soldCount).sum();
        map.put("totalSold", sold);
        map.put("status", m.status);
        map.put("banned", m.banUntil != null && m.banUntil.isAfter(LocalDateTime.now()));
        map.put("banUntil", m.banUntil);
        map.put("banReason", m.banReason);
        return map;
    }

    /**
     * 商家的商品列表。
     * status: PUBLISHED 在售 / OFF_SHELF 已下架 / PENDING 待审核 / REJECTED 被驳回 /
     *         SOLD 已售出(sold_count>0) / 空=全部
     */
    public List<Map<String, Object>> merchantProducts(Long merchantId, String status) {
        QueryWrapper<Product> q = new QueryWrapper<Product>().eq("seller_id", merchantId);
        if ("SOLD".equals(status)) {
            q.gt("sold_count", 0);
        } else if (status != null && !status.isBlank()) {
            q.eq("status", status);
        }
        q.orderByDesc("created_at");
        return enrichProducts(products.selectList(q));
    }

    /** 前台：查看某个商家的公开店铺（PUBLISHED 商品） */
    public Map<String, Object> publicShop(Long merchantId) {
        User m = users.selectById(merchantId);
        if (m == null) throw new IllegalArgumentException("商家不存在");
        QueryWrapper<Product> q = new QueryWrapper<Product>()
                .eq("seller_id", merchantId).eq("status", "PUBLISHED")
                .orderByDesc("sold_count");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("merchant", publicUser(m));
        map.put("products", enrichProducts(products.selectList(q)));
        return map;
    }

    // ================= 购物车 =================

    public List<Map<String, Object>> cart(Long userId) {
        List<Cart> list = carts.selectList(new QueryWrapper<Cart>().eq("user_id", userId));
        if (list.isEmpty()) return Collections.emptyList();
        Set<Long> pids = list.stream().map(c -> c.productId).collect(Collectors.toSet());
        Map<Long, Product> pMap = products.selectBatchIds(pids).stream()
                .collect(Collectors.toMap(p -> p.id, p -> p));
        Set<Long> sids = pMap.values().stream().map(p -> p.sellerId).collect(Collectors.toSet());
        Map<Long, User> sMap = users.selectBatchIds(sids).stream()
                .collect(Collectors.toMap(u -> u.id, this::publicUser));
        return list.stream().map(c -> {
            Product p = pMap.get(c.productId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cartId", c.id);
            m.put("quantity", c.quantity);
            m.put("product", p);
            m.put("seller", p != null ? sMap.get(p.sellerId) : null);
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addCart(Long userId, Long productId, int qty) {
        Product p = products.selectById(productId);
        if (p == null || !"PUBLISHED".equals(p.status)) throw new IllegalArgumentException("商品不可购买");
        if (qty <= 0) throw new IllegalArgumentException("数量必须大于0");
        checkBlacklist(userId, p.sellerId);
        Cart c = carts.selectOne(new QueryWrapper<Cart>()
                .eq("user_id", userId).eq("product_id", productId));
        if (c == null) {
            c = new Cart();
            c.userId = userId; c.productId = productId; c.quantity = qty;
            carts.insert(c);
        } else {
            c.quantity += qty;
            carts.updateById(c);
        }
    }

    @Transactional
    public void updateCartItem(Long cartId, Long userId, int qty) {
        Cart c = carts.selectById(cartId);
        if (c == null || !Objects.equals(c.userId, userId)) throw new IllegalArgumentException("购物车项不存在");
        if (qty <= 0) {
            carts.deleteById(cartId);
        } else {
            c.quantity = qty;
            carts.updateById(c);
        }
    }

    @Transactional
    public void clearCart(Long userId) {
        carts.delete(new QueryWrapper<Cart>().eq("user_id", userId));
    }

    // ================= 下单：积分抵扣 + 钱包扣款 + 中间账户 =================

    /** 购物车一键下单。pointsUsed 为用户想用的积分数量（可0）。 */
    @Transactional
    public List<Order> checkout(Long userId, Long pointsUsed) {
        List<Cart> list = cartRaw(userId);
        if (list.isEmpty()) throw new IllegalArgumentException("购物车为空");
        User buyer = users.selectById(userId);
        // 积分预扣
        Long actualPointsUsed = 0L;
        BigDecimal totalOriginal = BigDecimal.ZERO;

        // 校验每件商品状态、库存、黑名单
        List<Cart> valid = new ArrayList<>();
        Map<Long, Product> pCache = new HashMap<>();
        for (Cart c : list) {
            Product p = products.selectById(c.productId);
            pCache.put(c.productId, p);
            if (p == null || !"PUBLISHED".equals(p.status))
                throw new IllegalArgumentException("商品 #" + c.productId + " 已下架");
            if (p.stock < c.quantity)
                throw new IllegalArgumentException("商品「" + p.name + "」库存不足，当前仅剩 " + p.stock + " 件，购物车中有 " + c.quantity + " 件");
            checkBlacklist(userId, p.sellerId);
            valid.add(c);
            totalOriginal = totalOriginal.add(p.price.multiply(BigDecimal.valueOf(c.quantity)));
        }

        // 积分抵扣（100 积分 = 1 元）：严格校验，不做静默截断
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        if (pointsUsed != null && pointsUsed > 0) {
            if (pointsUsed > buyer.points)
                throw new IllegalArgumentException("积分不足：您当前仅有 " + buyer.points + " 积分，无法使用 " + pointsUsed + " 积分抵扣");
            if (pointsUsed % 100 != 0)
                throw new IllegalArgumentException("积分需按 100 的整数倍使用（100 积分 = 1 元），请调整为 " + (pointsUsed / 100 * 100) + " 或 " + ((pointsUsed / 100 + 1) * 100));
            long maxPointsByAmount = totalOriginal.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.DOWN).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
            if (pointsUsed > maxPointsByAmount)
                throw new IllegalArgumentException("本单最多可用 " + maxPointsByAmount + " 积分抵扣（订单金额 ￥" + totalOriginal + "，实付不能为负）");
            BigDecimal money = BigDecimal.valueOf(pointsUsed).divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
            pointsDiscount = money;
            actualPointsUsed = pointsUsed;
        }

        BigDecimal payAmount = totalOriginal.subtract(pointsDiscount);
        if (buyer.walletBalance.compareTo(payAmount) < 0)
            throw new IllegalArgumentException("钱包余额不足：本单需支付 ￥" + payAmount + "，当前余额 ￥" + buyer.walletBalance + "，还差 ￥" + payAmount.subtract(buyer.walletBalance));

        // 扣款 + 积分扣减（条件更新，防止并发下扣成负数）
        if (payAmount.compareTo(BigDecimal.ZERO) > 0) {
            int paid = users.update(null, new UpdateWrapper<User>()
                    .eq("id", userId).ge("wallet_balance", payAmount)
                    .setSql("wallet_balance = wallet_balance - " + payAmount));
            if (paid == 0) throw new IllegalArgumentException("钱包余额不足，请刷新后重试");
        }
        if (actualPointsUsed > 0) {
            int deducted = users.update(null, new UpdateWrapper<User>()
                    .eq("id", userId).ge("points", actualPointsUsed)
                    .setSql("points = points - " + actualPointsUsed));
            if (deducted == 0) throw new IllegalArgumentException("积分不足，请刷新后重试");
        }
        if (payAmount.compareTo(BigDecimal.ZERO) > 0)
            saveWalletRecord(userId, "PAY", payAmount.negate(), null, "购物车下单，原价 " + totalOriginal + "，积分抵 " + pointsDiscount);
        if (actualPointsUsed > 0)
            savePointRecord(userId, "DEDUCT", actualPointsUsed, null, "下单抵扣");

        // 逐一生成订单 + 中间账户暂存 + 扣库存
        List<Order> made = new ArrayList<>();
        BigDecimal allocatedDiscount = BigDecimal.ZERO;
        long allocatedPoints = 0L;

        for (Cart c : valid) {
            Product p = pCache.get(c.productId);
            BigDecimal orderOriginal = p.price.multiply(BigDecimal.valueOf(c.quantity));
            // 积分抵扣按比例分摊，最后一单吸收尾差（保证各订单合计 = 实付金额）
            boolean last = (valid.indexOf(c) == valid.size() - 1);
            BigDecimal orderDiscount = last
                    ? pointsDiscount.subtract(allocatedDiscount)
                    : orderOriginal.multiply(pointsDiscount).divide(totalOriginal, 2, RoundingMode.DOWN);
            long orderPointsDeduct = last
                    ? actualPointsUsed - allocatedPoints
                    : orderDiscount.multiply(new BigDecimal("100")).longValue();
            allocatedDiscount = allocatedDiscount.add(orderDiscount);
            allocatedPoints += orderPointsDeduct;
            BigDecimal orderAmount = orderOriginal.subtract(orderDiscount);

            // 条件更新扣库存，防止并发超卖
            int stockUpdated = products.update(null, new UpdateWrapper<Product>()
                    .eq("id", p.id).ge("stock", c.quantity)
                    .setSql("stock = stock - " + c.quantity)
                    .setSql("sold_count = sold_count + " + c.quantity));
            if (stockUpdated == 0)
                throw new IllegalArgumentException("商品「" + p.name + "」库存不足，请刷新后重试");
            Product fresh = products.selectById(p.id);
            if (fresh != null && fresh.stock != null && fresh.stock == 0) {
                products.update(null, new UpdateWrapper<Product>().eq("id", p.id).set("status", "LOCKED"));
            }

            String orderNo = "CM" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
            Order o = new Order();
            o.orderNo = orderNo;
            o.buyerId = userId;
            o.sellerId = p.sellerId;
            o.productId = p.id;
            o.productName = p.name;
            o.productImage = firstImage(p.id);
            o.quantity = c.quantity;
            o.amount = orderAmount;
            o.originalAmount = orderOriginal;
            o.pointsDeducted = orderPointsDeduct;
            o.status = "PAID_PENDING_RECEIVE";
            orders.insert(o);

            // 暂存到中间账户
            PlatformFund f = new PlatformFund();
            f.orderNo = orderNo;
            f.sellerId = p.sellerId;
            f.buyerId = userId;
            f.amount = orderAmount;
            f.fee = BigDecimal.ZERO;
            f.feeRate = BigDecimal.ZERO;
            f.status = "HELD";
            funds.insert(f);

            made.add(o);
        }

        // 清空购物车
        carts.delete(new QueryWrapper<Cart>().eq("user_id", userId));
        return made;
    }

    /** 老接口兼容：购物车一键下单（不传积分，默认 0） */
    @Transactional
    public List<Order> checkout(Long userId) {
        return checkout(userId, 0L);
    }

    private List<Cart> cartRaw(Long userId) {
        return carts.selectList(new QueryWrapper<Cart>().eq("user_id", userId));
    }

    private String firstImage(Long productId) {
        ProductImage pi = productImages.selectOne(new QueryWrapper<ProductImage>()
                .eq("product_id", productId).orderByAsc("sort_no").last("LIMIT 1"));
        return pi != null ? pi.imageUrl : null;
    }

    // ================= 订单列表 / 详情 =================

    /** 我的订单（买家视角） */
    public List<Order> myOrders(Long userId) {
        return orders.selectList(new QueryWrapper<Order>()
                .eq("buyer_id", userId).orderByDesc("created_at"));
    }

    /** 商家的出售订单 */
    public List<Order> sellerOrders(Long sellerId) {
        return orders.selectList(new QueryWrapper<Order>()
                .eq("seller_id", sellerId).orderByDesc("created_at"));
    }

    /** 平台全部订单（管理员） */
    public List<Order> allOrders() {
        return orders.selectList(new QueryWrapper<Order>().orderByDesc("created_at"));
    }

    // ================= 确认收货 + 中间账户结算 + 发积分 =================

    @Transactional
    public void ship(Long orderId, Long sellerId) {
        Order o = orders.selectById(orderId);
        if (o == null || !Objects.equals(o.sellerId, sellerId))
            throw new IllegalArgumentException("订单不存在或无权操作");
        if (!"PAID_PENDING_RECEIVE".equals(o.status))
            throw new IllegalArgumentException("订单当前状态不允许发货");
        o.status = "SHIPPED";
        orders.updateById(o);
    }

    @Transactional
    public void confirm(Long orderId, Long buyerId) {
        Order o = orders.selectById(orderId);
        if (o == null || !Objects.equals(o.buyerId, buyerId))
            throw new IllegalArgumentException("订单不存在");
        if (!"PAID_PENDING_RECEIVE".equals(o.status) && !"SHIPPED".equals(o.status))
            throw new IllegalArgumentException("订单当前状态不允许确认收货");
        o.status = "COMPLETED";
        o.receivedAt = LocalDateTime.now();
        o.completedAt = LocalDateTime.now();
        orders.updateById(o);
        settleOrder(o);
    }

    /** 后台定时任务入口：自动确认 + 结算（超过 7 天未收货） */
    @Transactional
    public void autoConfirmSettles() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<Order> overdue = orders.selectList(new QueryWrapper<Order>()
                .in("status", Arrays.asList("PAID_PENDING_RECEIVE", "SHIPPED"))
                .lt("created_at", threshold));
        for (Order o : overdue) {
            o.status = "COMPLETED";
            o.receivedAt = LocalDateTime.now();
            o.completedAt = LocalDateTime.now();
            orders.updateById(o);
            settleOrder(o);
        }
    }

    /** 结算：中间账户扣手续费 → 卖家钱包 + 平台流水 + 买家积分 */
    @Transactional
    public void settleOrder(Order o) {
        PlatformFund f = funds.selectOne(new QueryWrapper<PlatformFund>()
                .eq("order_no", o.orderNo));
        if (f == null) return;
        if (!"HELD".equals(f.status)) return;

        User seller = users.selectById(o.sellerId);
        // 查当前商家等级 → 费率
        int level = seller.merchantLevel == null ? 1 : seller.merchantLevel;
        MerchantLevel ml = levels.selectById(level);
        BigDecimal rate = ml != null ? ml.feeRate : new BigDecimal("0.001");
        BigDecimal fee = f.amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal toSeller = f.amount.subtract(fee);

        f.fee = fee;
        f.feeRate = rate;
        f.status = "SETTLED";
        f.settledAt = LocalDateTime.now();
        funds.updateById(f);

        // 卖家入账
        seller.walletBalance = seller.walletBalance.add(toSeller);
        users.updateById(seller);
        saveWalletRecord(seller.id, "SETTLE", toSeller, o.orderNo,
                "订单结算，商品金额 " + f.amount + "，手续费 " + fee + "（等级" + level + "，费率" + rate + "）");

        // 买家积分（消费多少元 → 多少分，不包含积分抵扣的部分）
        long earn = o.amount.setScale(0, RoundingMode.DOWN).longValue();
        if (earn > 0) {
            User buyer = users.selectById(o.buyerId);
            buyer.points += earn;
            users.updateById(buyer);
            savePointRecord(o.buyerId, "EARN", earn, o.orderNo, "消费获得积分");
        }
    }

    // ================= 退货：24 小时内申请 + 商家审核 =================

    /** 买家申请退货（需在确认收货 24 小时内；未确认收货但想取消也可以，此时走 PENDING 取消路径——这里统一在确认后） */
    @Transactional
    public Long applyReturn(Long orderId, Long buyerId, String reason) {
        Order o = orders.selectById(orderId);
        if (o == null || !Objects.equals(o.buyerId, buyerId))
            throw new IllegalArgumentException("订单不存在");
        // 只有已完成/已发货/待收货的订单可以申请退货
        if (!Arrays.asList("COMPLETED", "SHIPPED", "PAID_PENDING_RECEIVE").contains(o.status))
            throw new IllegalArgumentException("当前订单状态不可申请退货");
        // 防重复：同一订单同时只能存在一笔待处理退货，已退货订单不可再次申请
        Long pending = returns.selectCount(new QueryWrapper<Return>()
                .eq("order_id", orderId).eq("status", "PENDING"));
        if (pending != null && pending > 0)
            throw new IllegalArgumentException("该订单已有待处理的退货申请，请勿重复提交");
        // 24 小时窗口：从确认收货时间算起
        LocalDateTime deadline = o.receivedAt != null ? o.receivedAt.plusHours(24)
                : o.createdAt.plusHours(24);
        if (LocalDateTime.now().isAfter(deadline))
            throw new IllegalArgumentException("超过 24 小时退货时限，无法申请");
        Return r = new Return();
        r.orderId = o.id;
        r.orderNo = o.orderNo;
        r.buyerId = buyerId;
        r.sellerId = o.sellerId;
        r.reason = reason;
        r.status = "PENDING";
        returns.insert(r);
        o.status = "RETURN_PENDING";
        orders.updateById(o);
        return r.id;
    }

    /** 商家审核退货 */
    @Transactional
    public void reviewReturn(Long returnId, Long sellerId, boolean approved, String comment) {
        Return r = returns.selectById(returnId);
        if (r == null || !Objects.equals(r.sellerId, sellerId))
            throw new IllegalArgumentException("退货申请不存在");
        if (!"PENDING".equals(r.status))
            throw new IllegalArgumentException("退货申请已处理");
        Order o = orders.selectById(r.orderId);
        if (!approved) {
            r.status = "REJECTED";
            r.rejectedAt = LocalDateTime.now();
            returns.updateById(r);
            o.status = "COMPLETED"; // 回到已完成
            orders.updateById(o);
            // 若资金仍在托管（未结算），按正常完成流程结算给卖家，避免订单已完成但钱一直挂在平台
            settleOrder(o);
            return;
        }
        // 批准 → 退款给买家；若订单已结算给卖家，则从卖家钱包扣回
        r.status = "APPROVED";
        r.approvedAt = LocalDateTime.now();
        returns.updateById(r);
        o.status = "RETURNED";
        orders.updateById(o);

        User buyer = users.selectById(o.buyerId);
        buyer.walletBalance = buyer.walletBalance.add(o.amount);
        users.updateById(buyer);
        saveWalletRecord(buyer.id, "REFUND", o.amount, o.orderNo, "退货退款：" + (comment != null ? comment : "商家同意"));

        PlatformFund f = funds.selectOne(new QueryWrapper<PlatformFund>().eq("order_no", o.orderNo));
        if (f != null) {
            if ("SETTLED".equals(f.status)) {
                // 已结算：把卖家实际收到的钱扣回（成交金额 - 平台手续费）
                BigDecimal back = f.amount.subtract(f.fee == null ? BigDecimal.ZERO : f.fee);
                if (back.compareTo(BigDecimal.ZERO) > 0) {
                    users.update(null, new UpdateWrapper<User>().eq("id", o.sellerId)
                            .setSql("wallet_balance = wallet_balance - " + back));
                    saveWalletRecord(o.sellerId, "REFUND", back.negate(), o.orderNo, "退货退款扣回");
                }
                // 已结算订单已按实付金额发放消费积分，退货时同步扣回
                long earn = o.amount.setScale(0, RoundingMode.DOWN).longValue();
                if (earn > 0) {
                    users.update(null, new UpdateWrapper<User>().eq("id", o.buyerId)
                            .setSql("points = GREATEST(points - " + earn + ", 0)"));
                    savePointRecord(o.buyerId, "DEDUCT", earn, o.orderNo, "退货退款扣回消费积分");
                }
            }
            if ("HELD".equals(f.status) || "SETTLED".equals(f.status)) {
                f.status = "REFUNDED";
                f.settledAt = LocalDateTime.now();
                funds.updateById(f);
            }
        }
    }

    /** 买家的退货记录 */
    public List<Return> myReturns(Long userId) {
        return returns.selectList(new QueryWrapper<Return>()
                .eq("buyer_id", userId).orderByDesc("created_at"));
    }

    /** 商家待处理退货 */
    public List<Return> sellerReturns(Long sellerId) {
        return returns.selectList(new QueryWrapper<Return>()
                .eq("seller_id", sellerId).orderByDesc("created_at"));
    }

    // ================= 评价 =================

    /**
     * 下单后评价。一个订单允许：
     *  - 买家 → 商品 （BUYER_TO_PRODUCT）
     *  - 买家 → 商家服务态度 （BUYER_TO_SELLER）
     *  - 商家 → 买家 （SELLER_TO_BUYER）
     */
    @Transactional
    public Long submitReview(Long orderId, Long reviewerId, String reviewType,
                              Integer rating, String content) {
        Order o = orders.selectById(orderId);
        if (o == null) throw new IllegalArgumentException("订单不存在");
        // 已完成（COMPLETED）或已退货（RETURNED）的订单均允许评价：
        // 买家可评价商品与商家服务态度，商家可评价买家的购买/退货行为
        if (!"COMPLETED".equals(o.status) && !"RETURNED".equals(o.status))
            throw new IllegalArgumentException("订单未完成或未退货，暂不能评价");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("评分必须在 1~5 之间");
        if (reviewType == null) throw new IllegalArgumentException("未知评价类型");
        // 同一 reviewer + order + type 不允许重复
        Long exists = reviews.selectCount(new QueryWrapper<Review>()
                .eq("order_id", orderId).eq("reviewer_id", reviewerId).eq("review_type", reviewType));
        if (exists > 0) throw new IllegalArgumentException("您已评价过");

        Review rv = new Review();
        rv.orderId = orderId;
        rv.orderNo = o.orderNo;
        rv.reviewerId = reviewerId;
        rv.productId = o.productId;
        rv.rating = rating;
        rv.content = content;
        rv.reviewType = reviewType;

        switch (reviewType) {
            case "BUYER_TO_PRODUCT" -> {
                if (!Objects.equals(reviewerId, o.buyerId)) throw new IllegalArgumentException("只能由订单买家评价商品");
                rv.targetUserId = null;
            }
            case "BUYER_TO_SELLER" -> {
                if (!Objects.equals(reviewerId, o.buyerId)) throw new IllegalArgumentException("只能由买家评价");
                rv.targetUserId = o.sellerId;
            }
            case "SELLER_TO_BUYER" -> {
                if (!Objects.equals(reviewerId, o.sellerId)) throw new IllegalArgumentException("只能由商家评价");
                rv.targetUserId = o.buyerId;
            }
            default -> throw new IllegalArgumentException("未知评价类型");
        }
        reviews.insert(rv);

        // 更新被评价者 goodRate
        if (rv.targetUserId != null) recalcGoodRate(rv.targetUserId);
        return rv.id;
    }

    /** 重新计算某用户的 goodRate（基于所有对他的评分均值） */
    private void recalcGoodRate(Long userId) {
        List<Review> all = reviews.selectList(new QueryWrapper<Review>().eq("target_user_id", userId));
        if (all.isEmpty()) return;
        double avg = all.stream().mapToInt(r -> r.rating).average().orElse(0);
        BigDecimal rate = BigDecimal.valueOf(avg * 20).setScale(2, RoundingMode.HALF_UP); // 5星=100分
        User u = users.selectById(userId);
        u.goodRate = rate;
        users.updateById(u);
    }

    /** 商品评价列表（买家对商品的） */
    public List<Review> productReviews(Long productId) {
        return reviews.selectList(new QueryWrapper<Review>()
                .eq("product_id", productId).eq("review_type", "BUYER_TO_PRODUCT")
                .orderByDesc("created_at"));
    }

    /** 评价详情 */
    public Review reviewDetail(Long id) {
        Review r = reviews.selectById(id);
        if (r == null) throw new IllegalArgumentException("评价不存在");
        return r;
    }

    /** 商家服务态度评价 */
    public List<Review> sellerServiceReviews(Long sellerId) {
        return reviews.selectList(new QueryWrapper<Review>()
                .eq("target_user_id", sellerId).eq("review_type", "BUYER_TO_SELLER")
                .orderByDesc("created_at"));
    }

    // ================= 议价 / 联系商家 =================

    /** 买家对商品发起议价或留言。offerPrice 可为空（纯咨询）。 */
    @Transactional
    public Long createNegotiation(Long buyerId, Long productId, BigDecimal offerPrice, String message) {
        Product p = products.selectById(productId);
        if (p == null || !"PUBLISHED".equals(p.status))
            throw new IllegalArgumentException("商品不存在或已下架");
        if (Objects.equals(p.sellerId, buyerId))
            throw new IllegalArgumentException("不能对自己的商品发起议价");
        if ((offerPrice == null || offerPrice.signum() <= 0) && (message == null || message.isBlank()))
            throw new IllegalArgumentException("请填写期望价格或留言内容");
        if (offerPrice != null && offerPrice.signum() <= 0)
            throw new IllegalArgumentException("期望价格必须大于 0");
        if (offerPrice != null && p.price != null && offerPrice.compareTo(p.price) >= 0)
            throw new IllegalArgumentException("期望价格需低于当前售价 ￥" + p.price + "，否则可直接下单");
        checkBlacklist(buyerId, p.sellerId);

        Negotiation n = new Negotiation();
        n.productId = productId;
        n.buyerId = buyerId;
        n.sellerId = p.sellerId;
        n.offerPrice = offerPrice;
        n.message = message;
        n.status = "PENDING";
        negotiations.insert(n);
        return n.id;
    }

    public List<Map<String, Object>> buyerNegotiations(Long buyerId) {
        return enrichNegotiations(negotiations.selectList(new QueryWrapper<Negotiation>()
                .eq("buyer_id", buyerId).orderByDesc("created_at")));
    }

    public List<Map<String, Object>> sellerNegotiations(Long sellerId) {
        return enrichNegotiations(negotiations.selectList(new QueryWrapper<Negotiation>()
                .eq("seller_id", sellerId).orderByDesc("created_at")));
    }

    private List<Map<String, Object>> enrichNegotiations(List<Negotiation> list) {
        if (list.isEmpty()) return Collections.emptyList();
        Set<Long> pids = list.stream().map(n -> n.productId).collect(Collectors.toSet());
        Set<Long> uids = new HashSet<>();
        list.forEach(n -> { uids.add(n.buyerId); uids.add(n.sellerId); });
        Map<Long, Product> pMap = products.selectBatchIds(pids).stream()
                .collect(Collectors.toMap(x -> x.id, x -> x));
        Map<Long, User> uMap = users.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(x -> x.id, this::safe));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Negotiation n : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("negotiation", n);
            m.put("product", pMap.get(n.productId));
            m.put("buyer", uMap.get(n.buyerId));
            m.put("seller", uMap.get(n.sellerId));
            m.put("coverImage", firstImage(n.productId));
            out.add(m);
        }
        return out;
    }

    /** 商家回复议价；accepted=true 表示同意按买家期望价成交（仅作记录） */
    @Transactional
    public void replyNegotiation(Long id, Long sellerId, String reply, Boolean accepted) {
        Negotiation n = negotiations.selectById(id);
        if (n == null || !Objects.equals(n.sellerId, sellerId))
            throw new IllegalArgumentException("议价记录不存在或无权操作");
        if (reply == null || reply.isBlank())
            throw new IllegalArgumentException("请填写回复内容");
        n.reply = reply;
        n.status = Boolean.TRUE.equals(accepted) ? "ACCEPTED" : "REPLIED";
        n.repliedAt = LocalDateTime.now();
        negotiations.updateById(n);
    }

    // ================= 钱包 / 积分流水 =================

    public List<WalletRecord> walletRecords(Long userId) {
        return walletRecords.selectList(new QueryWrapper<WalletRecord>()
                .eq("user_id", userId).orderByDesc("created_at"));
    }

    public List<PointRecord> pointRecords(Long userId) {
        return pointRecords.selectList(new QueryWrapper<PointRecord>()
                .eq("user_id", userId).orderByDesc("created_at"));
    }

    // ================= 统计 / 仪表盘 =================

    public Map<String, Object> adminDashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", users.selectCount(null));
        m.put("merchantCount", users.selectCount(new QueryWrapper<User>().eq("role", "MERCHANT")));
        m.put("pendingAuditCount", users.selectCount(new QueryWrapper<User>().eq("audit_status", "PENDING")));
        m.put("pendingProductCount", products.selectCount(new QueryWrapper<Product>().eq("status", "PENDING")));
        m.put("orderCount", orders.selectCount(null));
        List<PlatformFund> heldFunds = funds.selectList(new QueryWrapper<PlatformFund>().eq("status", "HELD"));
        m.put("platformFundSum", heldFunds.stream().map(f -> f.amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("platformFundCount", heldFunds.size());
        m.put("platformSettledSum", funds.selectList(new QueryWrapper<PlatformFund>().eq("status", "SETTLED"))
                .stream().map(f -> f.amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("platformRefundedSum", funds.selectList(new QueryWrapper<PlatformFund>().eq("status", "REFUNDED"))
                .stream().map(f -> f.amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("platformFeeSum", funds.selectList(new QueryWrapper<PlatformFund>().eq("status", "SETTLED"))
                .stream().map(f -> f.fee == null ? BigDecimal.ZERO : f.fee).reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    public Map<String, Object> merchantDashboard(Long merchantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        QueryWrapper<Order> sellerQ = new QueryWrapper<Order>().eq("seller_id", merchantId);
        m.put("orderCount", orders.selectCount(sellerQ));
        m.put("completedOrderCount", orders.selectCount(new QueryWrapper<Order>()
                .eq("seller_id", merchantId).eq("status", "COMPLETED")));
        m.put("pendingReceive", orders.selectCount(new QueryWrapper<Order>()
                .eq("seller_id", merchantId).eq("status", "PAID_PENDING_RECEIVE")));
        // 中间账户待结算金额
        List<PlatformFund> held = funds.selectList(new QueryWrapper<PlatformFund>()
                .eq("seller_id", merchantId).eq("status", "HELD"));
        BigDecimal heldSum = held.stream().map(f -> f.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("heldAmount", heldSum);
        m.put("merchant", safe(users.selectById(merchantId)));
        return m;
    }
}
