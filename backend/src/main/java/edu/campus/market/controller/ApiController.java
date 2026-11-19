package edu.campus.market.controller;

import edu.campus.market.common.ApiResult;
import edu.campus.market.entity.*;
import edu.campus.market.mapper.*;
import edu.campus.market.service.MarketService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final MarketService service;
    private final UserMapper users;
    private final ProductMapper products;

    public ApiController(MarketService s, UserMapper u, ProductMapper p) {
        service = s; users = u; products = p;
    }

    // ============ 鉴权工具 ============
    private Long uid(String token) {
        try {
            return Long.valueOf(token.split(":")[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException("请先登录，传入请求头 X-User-Token");
        }
    }

    private boolean admin(String token) {
        if (token == null || !token.endsWith(":ADMIN")) return false;
        try {
            User u = users.selectById(uid(token));
            return u != null && "ADMIN".equals(u.role) && "APPROVED".equals(u.auditStatus);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean merchant(String token) {
        if (token == null || !(token.endsWith(":MERCHANT") || token.endsWith(":ADMIN"))) return false;
        try {
            User u = users.selectById(uid(token));
            return u != null && ("MERCHANT".equals(u.role) || "ADMIN".equals(u.role))
                    && "APPROVED".equals(u.auditStatus);
        } catch (Exception e) {
            return false;
        }
    }

    private String role(String token) {
        if (token == null || !token.contains(":")) return "USER";
        return token.substring(token.indexOf(':') + 1);
    }

    // ================= 全局消息 / 待办角标 =================
    @GetMapping("/notifications/summary")
    public ApiResult<?> notificationSummary(@RequestHeader("X-User-Token") String token,
                                             @RequestParam(required = false) Long seenNegoId,
                                             @RequestParam(required = false) Long seenReturnId) {
        return ApiResult.ok(service.notificationSummary(uid(token), role(token), seenNegoId, seenReturnId));
    }


    // ================= 健康检查 =================
    @GetMapping("/health")
    public ApiResult<?> health() {
        return ApiResult.ok(Map.of("application", "campus-market", "status", "running"));
    }

    // ================= 注册 / 登录 =================

    /** 注册：需带 captchaKey（由 /captcha 返回）和 captchaCode（用户输入） */
    @PostMapping("/auth/register")
    public ApiResult<?> register(@RequestBody Map<String, Object> body) {
        // 验证码由 /captcha 生成并写入内存缓存，这里是唯一校验入口：
        // 校验通过才继续注册，且每个验证码只能使用一次。
        String ck = Objects.toString(body.get("captchaKey"), "").trim();
        String cc = Objects.toString(body.get("captchaCode"), "").trim();
        if (cc.isEmpty()) throw new IllegalArgumentException("请填写图片验证码");
        if (ck.isEmpty()) throw new IllegalArgumentException("图片验证码已失效，请点击图片刷新后重新输入");
        if (!CaptchaService.verify(ck, cc))
            throw new IllegalArgumentException("图片验证码不正确或已过期，请点击图片刷新后重新输入");
        service.register(body);
        return ApiResult.ok("注册成功，请等待管理员审核");
    }

    @PostMapping("/auth/login")
    public ApiResult<?> login(@RequestBody Map<String, String> b) {
        return ApiResult.ok(service.login(b.get("username"), b.get("password")));
    }

    // ================= 我的信息 / 钱包 / 积分 =================

    @GetMapping("/users/me")
    public ApiResult<?> me(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.safe(users.selectById(uid(token))));
    }

    // ================= 账号注销（需管理员审核） =================

    /** 提交账号注销申请 */
    @PostMapping("/users/me/cancel")
    public ApiResult<?> requestCancellation(@RequestHeader("X-User-Token") String token,
                                            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : Objects.toString(body.get("reason"), null);
        service.requestCancellation(uid(token), reason);
        return ApiResult.ok(true);
    }

    /** 查看自己的注销申请状态 */
    @GetMapping("/users/me/cancel")
    public ApiResult<?> myCancellation(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.myCancellation(uid(token)));
    }

    /** 管理员：注销申请列表 */
    @GetMapping("/admin/cancellations")
    public ApiResult<?> adminCancellations(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminCancellations());
    }

    /** 管理员：审核注销申请 */
    @PostMapping("/admin/cancellations/{id}/review")
    public ApiResult<?> reviewCancellation(@RequestHeader("X-User-Token") String token,
                                           @PathVariable Long id,
                                           @RequestParam boolean approved,
                                           @RequestParam(required = false) String note) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.reviewCancellation(id, approved, note, uid(token));
        return ApiResult.ok(true);
    }

    @GetMapping("/users/{id}/wallet/records")
    public ApiResult<?> walletRecords(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!Objects.equals(uid(token), id) && !admin(token))
            throw new IllegalArgumentException("无权查看");
        return ApiResult.ok(service.walletRecords(id));
    }

    @GetMapping("/users/{id}/point/records")
    public ApiResult<?> pointRecords(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!Objects.equals(uid(token), id) && !admin(token))
            throw new IllegalArgumentException("无权查看");
        return ApiResult.ok(service.pointRecords(id));
    }

    // ================= 商品：搜索 / 详情 / 轮播 =================

    /** 前台搜索：keyword + sort(price_asc/price_desc/sales/default) */
    @GetMapping("/products")
    public ApiResult<?> products(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.searchProducts(keyword, sort));
    }

    @GetMapping("/products/carousel")
    public ApiResult<?> carousel() {
        return ApiResult.ok(service.carouselProducts());
    }

    @GetMapping("/products/{id}")
    public ApiResult<?> product(@PathVariable Long id) {
        return ApiResult.ok(service.productDetail(id));
    }

    /** 商家评价列表（BUYER_TO_PRODUCT） */
    @GetMapping("/products/{id}/reviews")
    public ApiResult<?> productReviews(@PathVariable Long id) {
        return ApiResult.ok(service.productReviews(id));
    }

    // ================= 店铺 =================

    @GetMapping("/shops/{merchantId}")
    public ApiResult<?> publicShop(@PathVariable Long merchantId) {
        return ApiResult.ok(service.publicShop(merchantId));
    }

    @GetMapping("/shops/{merchantId}/reviews")
    public ApiResult<?> sellerServiceReviews(@PathVariable Long merchantId) {
        return ApiResult.ok(service.sellerServiceReviews(merchantId));
    }

    // ================= 商品发布 / 审核 / 下架 =================

    @PostMapping("/products")
    public ApiResult<?> createProduct(@RequestHeader("X-User-Token") String token,
                                      @RequestBody Map<String, Object> b) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可发布商品");
        return ApiResult.ok(service.createProduct(uid(token), b));
    }

    @PostMapping("/products/{id}/take-down")
    public ApiResult<?> takeDown(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        service.takeDown(id, uid(token));
        return ApiResult.ok(true);
    }

    /** 商家编辑商品：保存后自动重新提交管理员审核 */
    @PutMapping("/products/{id}")
    public ApiResult<?> updateProduct(@RequestHeader("X-User-Token") String token,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可编辑商品");
        return ApiResult.ok(service.updateProduct(id, uid(token), body));
    }

    /** 商家重新上架：重新提交管理员审核 */
    @PostMapping("/products/{id}/republish")
    public ApiResult<?> republish(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        service.republish(id, uid(token));
        return ApiResult.ok(true);
    }

    // ================= 议价 / 联系商家 =================

    /** 买家发起议价或留言 */
    @PostMapping("/negotiations")
    public ApiResult<?> createNegotiation(@RequestHeader("X-User-Token") String token,
                                          @RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(Objects.toString(body.get("productId")));
        BigDecimal offer = (body.get("offerPrice") == null || Objects.toString(body.get("offerPrice")).isBlank())
                ? null : new BigDecimal(Objects.toString(body.get("offerPrice")));
        String msg = Objects.toString(body.get("message"), null);
        return ApiResult.ok(service.createNegotiation(uid(token), productId, offer, msg));
    }

    /** 我的议价/留言（买家） */
    @GetMapping("/negotiations")
    public ApiResult<?> myNegotiations(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.buyerNegotiations(uid(token)));
    }

    /** 商家收到的议价/留言 */
    @GetMapping("/merchant/negotiations")
    public ApiResult<?> sellerNegotiations(@RequestHeader("X-User-Token") String token) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可操作");
        return ApiResult.ok(service.sellerNegotiations(uid(token)));
    }

    /** 商家回复议价 */
    @PostMapping("/negotiations/{id}/reply")
    public ApiResult<?> replyNegotiation(@RequestHeader("X-User-Token") String token,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可操作");
        service.replyNegotiation(id, uid(token),
                Objects.toString(body.get("reply"), null),
                Boolean.parseBoolean(Objects.toString(body.get("accepted"), "false")));
        return ApiResult.ok(true);
    }

    /** 商家自己的商品列表。status 可选：PUBLISHED / OFF_SHELF / PENDING / REJECTED */
    @GetMapping("/merchant/products")
    public ApiResult<?> merchantProducts(@RequestHeader("X-User-Token") String token,
                                         @RequestParam(required = false) String status) {
        return ApiResult.ok(service.merchantProducts(uid(token), status));
    }

    @GetMapping("/merchant/shop")
    public ApiResult<?> merchantShop(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.merchantShop(uid(token)));
    }

    @GetMapping("/merchant/dashboard")
    public ApiResult<?> merchantDashboard(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.merchantDashboard(uid(token)));
    }

    // ================= 购物车 =================

    @PostMapping("/cart/{productId}")
    public ApiResult<?> addCart(@RequestHeader("X-User-Token") String token,
                                @PathVariable Long productId,
                                @RequestParam(defaultValue = "1") int quantity) {
        service.addCart(uid(token), productId, quantity);
        return ApiResult.ok(true);
    }

    @GetMapping("/cart")
    public ApiResult<?> cart(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.cart(uid(token)));
    }

    @PutMapping("/cart/{cartId}")
    public ApiResult<?> updateCart(@RequestHeader("X-User-Token") String token,
                                   @PathVariable Long cartId,
                                   @RequestParam int quantity) {
        service.updateCartItem(cartId, uid(token), quantity);
        return ApiResult.ok(true);
    }

    @DeleteMapping("/cart/{cartId}")
    public ApiResult<?> deleteCartItem(@RequestHeader("X-User-Token") String token,
                                       @PathVariable Long cartId) {
        service.updateCartItem(cartId, uid(token), 0);
        return ApiResult.ok(true);
    }

    @DeleteMapping("/cart")
    public ApiResult<?> clearCart(@RequestHeader("X-User-Token") String token) {
        service.clearCart(uid(token));
        return ApiResult.ok(true);
    }

    // ================= 订单 =================

    /** 下单：POST /api/orders/checkout?pointsUsed=xxx 或 body 中 pointsUsed */
    @PostMapping("/orders/checkout")
    public ApiResult<?> checkout(@RequestHeader("X-User-Token") String token,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Long pointsUsed = body == null ? 0L : (body.get("pointsUsed") == null ? 0L
                : Long.valueOf(body.get("pointsUsed").toString()));
        return ApiResult.ok(service.checkout(uid(token), pointsUsed));
    }

    @GetMapping("/orders")
    public ApiResult<?> orders(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.myOrders(uid(token)));
    }

    @GetMapping("/orders/seller")
    public ApiResult<?> sellerOrders(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.sellerOrders(uid(token)));
    }

    @PostMapping("/orders/{id}/ship")
    public ApiResult<?> ship(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        merchant(token);
        service.ship(id, uid(token));
        return ApiResult.ok(true);
    }

    @PostMapping("/orders/{id}/confirm")
    public ApiResult<?> confirm(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        service.confirm(id, uid(token));
        return ApiResult.ok(true);
    }

    // ================= 退货 =================

    /** 买家申请退货 */
    @PostMapping("/orders/{id}/return")
    public ApiResult<?> applyReturn(@RequestHeader("X-User-Token") String token,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, String> body) {
        return ApiResult.ok(service.applyReturn(id, uid(token), body.get("reason")));
    }

    @GetMapping("/returns")
    public ApiResult<?> myReturns(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.myReturns(uid(token)));
    }

    @GetMapping("/returns/seller")
    public ApiResult<?> sellerReturns(@RequestHeader("X-User-Token") String token) {
        return ApiResult.ok(service.sellerReturns(uid(token)));
    }

    /** 商家审核退货 */
    @PostMapping("/returns/{id}/review")
    public ApiResult<?> reviewReturn(@RequestHeader("X-User-Token") String token,
                                     @PathVariable Long id,
                                     @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.parseBoolean(Objects.toString(body.get("approved")));
        service.reviewReturn(id, uid(token), approved, Objects.toString(body.get("comment"), null));
        return ApiResult.ok(true);
    }

    // ================= 评价 =================

    @PostMapping("/reviews")
    public ApiResult<?> submitReview(@RequestHeader("X-User-Token") String token,
                                     @RequestBody Map<String, Object> body) {
        return ApiResult.ok(service.submitReview(
                Long.valueOf(body.get("orderId").toString()),
                uid(token),
                Objects.toString(body.get("reviewType")),
                Integer.valueOf(body.get("rating").toString()),
                Objects.toString(body.get("content"), null)));
    }

    @GetMapping("/reviews/{id}")
    public ApiResult<?> review(@PathVariable Long id) {
        return ApiResult.ok(service.reviewDetail(id));
    }

    // ================= 管理员：仪表盘 =================

    @GetMapping("/admin/dashboard")
    public ApiResult<?> dashboard(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminDashboard());
    }

    @GetMapping("/admin/orders")
    public ApiResult<?> allOrders(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.allOrders());
    }

    // ================= 管理员：用户管理 =================

    @GetMapping("/admin/users")
    public ApiResult<?> allUsers(@RequestHeader("X-User-Token") String token,
                                 @RequestParam(required = false) String keyword) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.allUsers(keyword));
    }

    @GetMapping("/admin/users/pending")
    public ApiResult<?> pendingUsers(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.pendingUsers());
    }

    /** 管理员查看用户完整注册资料（营业执照/身份证/银行卡等） */
    @GetMapping("/admin/users/{id}")
    public ApiResult<?> adminUserDetail(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminUserDetail(id));
    }

    /** 管理员查看商家列表（好评率/服务评价/订单数），用于调整等级 */
    @GetMapping("/admin/merchants")
    public ApiResult<?> adminMerchants(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminMerchants());
    }

    @PostMapping("/admin/users/{id}/audit")
    public ApiResult<?> auditUser(@RequestHeader("X-User-Token") String token,
                                  @PathVariable Long id, @RequestParam boolean approved) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.auditUser(id, approved, uid(token));
        return ApiResult.ok(true);
    }

    @PutMapping("/admin/users/{id}")
    public ApiResult<?> updateUser(@RequestHeader("X-User-Token") String token,
                                   @PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.updateUser(id, body, uid(token));
        return ApiResult.ok(true);
    }

    @DeleteMapping("/admin/users/{id}")
    public ApiResult<?> deleteUser(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.deleteUser(id, uid(token));
        return ApiResult.ok(true);
    }

    /** 管理员充值：amount 正数 */
    @PostMapping("/admin/users/{id}/recharge")
    public ApiResult<?> recharge(@RequestHeader("X-User-Token") String token,
                                 @PathVariable Long id,
                                 @RequestParam BigDecimal amount) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.recharge(id, amount, uid(token));
        return ApiResult.ok(true);
    }

    /** 管理员封禁：?until=2025-12-31T23:59:59 传时间，空表示永久；&reason=xxx */
    @PostMapping("/admin/users/{id}/ban")
    public ApiResult<?> banUser(@RequestHeader("X-User-Token") String token,
                                @PathVariable Long id,
                                @RequestParam(required = false) String until,
                                @RequestParam(required = false) String reason) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        LocalDateTime t = (until == null || until.isBlank()) ? null : LocalDateTime.parse(until);
        service.banUser(id, t, reason, uid(token));
        return ApiResult.ok(true);
    }

    @PostMapping("/admin/users/{id}/unban")
    public ApiResult<?> unbanUser(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.unbanUser(id, uid(token));
        return ApiResult.ok(true);
    }

    // ================= 管理员：黑名单 =================

    @PostMapping("/admin/blacklists")
    public ApiResult<?> addBlacklistByAdmin(@RequestHeader("X-User-Token") String token,
                                            @RequestBody Map<String, Object> body) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        Long buyerId = Long.valueOf(body.get("buyerId").toString());
        Long sellerId = body.get("sellerId") == null ? null : Long.valueOf(body.get("sellerId").toString());
        service.addBlacklist(buyerId, sellerId, "SITE", Objects.toString(body.get("reason"), ""), uid(token));
        return ApiResult.ok(true);
    }

    /** 管理员查看全部黑名单 */
    @GetMapping("/admin/blacklists")
    public ApiResult<?> adminBlacklists(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminBlacklists());
    }

    @DeleteMapping("/admin/blacklists/{id}")
    public ApiResult<?> removeBlacklistByAdmin(@RequestHeader("X-User-Token") String token,
                                               @PathVariable Long id) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.removeBlacklist(id, uid(token));
        return ApiResult.ok(true);
    }

    /** 商家拉黑（商家操作，仅对自己店铺生效 SELLER 级） */
    @PostMapping("/merchant/blacklists")
    public ApiResult<?> merchantAddBlacklist(@RequestHeader("X-User-Token") String token,
                                              @RequestBody Map<String, Object> body) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可操作");
        Long buyerId = Long.valueOf(body.get("buyerId").toString());
        service.addBlacklist(buyerId, uid(token), "SELLER", Objects.toString(body.get("reason"), ""), uid(token));
        return ApiResult.ok(true);
    }

    /** 管理员：全部商品（可按状态过滤），用于轮播推荐 / 强制下架 */
    @GetMapping("/admin/products")
    public ApiResult<?> adminProducts(@RequestHeader("X-User-Token") String token,
                                      @RequestParam(required = false) String status) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.adminProducts(status));
    }

    /** 管理员强制下架商品 */
    @PostMapping("/admin/products/{id}/take-down")
    public ApiResult<?> adminTakeDown(@RequestHeader("X-User-Token") String token,
                                      @PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.adminTakeDown(id, reason);
        return ApiResult.ok(true);
    }

    /** 管理员关闭店铺：全部商品下架、店铺暂时关闭 */
    @PostMapping("/admin/merchants/{id}/close-shop")
    public ApiResult<?> closeShop(@RequestHeader("X-User-Token") String token,
                                  @PathVariable Long id,
                                  @RequestParam(required = false) String reason) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.closeShop(id, reason, uid(token));
        return ApiResult.ok(true);
    }

    /** 管理员恢复店铺 */
    @PostMapping("/admin/merchants/{id}/open-shop")
    public ApiResult<?> openShop(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.openShop(id, uid(token));
        return ApiResult.ok(true);
    }

    /** 商家查看自己拉黑的买家 */
    @GetMapping("/merchant/blacklists")
    public ApiResult<?> merchantBlacklists(@RequestHeader("X-User-Token") String token) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可操作");
        return ApiResult.ok(service.merchantBlacklists(uid(token)));
    }

    /** 商家移除自己拉黑的买家 */
    @DeleteMapping("/merchant/blacklists/{id}")
    public ApiResult<?> merchantRemoveBlacklist(@RequestHeader("X-User-Token") String token,
                                                 @PathVariable Long id) {
        if (!merchant(token)) throw new IllegalArgumentException("仅商家可操作");
        service.removeMerchantBlacklist(id, uid(token), uid(token));
        return ApiResult.ok(true);
    }

    // ================= 管理员：商品审核 / 轮播 =================

    @GetMapping("/admin/products/pending")
    public ApiResult<?> pendingProducts(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.pendingProducts());
    }

    @PostMapping("/admin/products/{id}/audit")
    public ApiResult<?> auditProduct(@RequestHeader("X-User-Token") String token,
                                     @PathVariable Long id,
                                     @RequestParam boolean approved,
                                     @RequestParam(required = false) String reason) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.auditProduct(id, approved, reason);
        return ApiResult.ok(true);
    }

    @PostMapping("/admin/products/{id}/carousel")
    public ApiResult<?> toggleCarousel(@RequestHeader("X-User-Token") String token,
                                       @PathVariable Long id,
                                       @RequestParam boolean on) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.toggleCarousel(id, on);
        return ApiResult.ok(true);
    }

    // ================= 管理员：商家等级 =================

    @GetMapping("/admin/merchant-levels")
    public ApiResult<?> merchantLevels(@RequestHeader("X-User-Token") String token) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        return ApiResult.ok(service.allMerchantLevels());
    }

    @PostMapping("/admin/merchants/{merchantId}/level")
    public ApiResult<?> setMerchantLevel(@RequestHeader("X-User-Token") String token,
                                         @PathVariable Long merchantId,
                                         @RequestParam int level) {
        if (!admin(token)) throw new IllegalArgumentException("仅管理员可操作");
        service.setMerchantLevel(merchantId, level, uid(token));
        return ApiResult.ok(true);
    }
}
