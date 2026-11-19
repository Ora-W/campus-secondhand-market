package edu.campus.market.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台定时任务：
 * 1) 每分钟扫描超过 7 天未收货的 PAID_PENDING_RECEIVE 订单，自动确认 + 结算到卖家钱包；
 * 2) 每 10 分钟清理过期验证码（可选）。
 */
@Component
public class OrderSettlementJob {

    private final MarketService service;

    public OrderSettlementJob(MarketService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void autoConfirm() {
        try {
            service.autoConfirmSettles();
        } catch (Exception e) {
            // 定时任务异常吞掉，避免阻塞
            e.printStackTrace();
        }
    }

    @Scheduled(fixedDelay = 600_000L)
    public void cleanupCaptcha() {
        edu.campus.market.controller.CaptchaService.cleanup();
    }
}
