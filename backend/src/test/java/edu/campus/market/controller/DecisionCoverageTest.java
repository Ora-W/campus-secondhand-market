
package edu.campus.market.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DecisionCoverageTest {

    // 生成一个有效的验证码 key
    private String createValidKey() {
        return CaptchaService.put("ABC123");
    }

    // 生成一个缓存中不存在的 key
    private String createNotExistKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 生成一个已经过期的验证码 key
    private String createExpiredKey() throws Exception {
        String key = CaptchaService.put("ABC123");

        Field field = CaptchaService.class.getDeclaredField("STORE");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> store =
                (Map<String, String>) field.get(null);

        // 将时间设置为 301 秒前，模拟验证码过期
        long expiredTime = System.currentTimeMillis() - 301_000;
        store.put(key, "ABC123|" + expiredTime);

        return key;
    }

    // D01：key 为空，J1 为真
    @Test
    void testD01() {
        assertFalse(CaptchaService.verify(null, "ABC123"));
    }

    // D02：key 不存在，J2 为真
    @Test
    void testD02() {
        assertFalse(CaptchaService.verify(
                createNotExistKey(), "ABC123"));
    }

    // D03：验证码已经过期，J3 为真
    @Test
    void testD03() throws Exception {
        assertFalse(CaptchaService.verify(
                createExpiredKey(), "ABC123"));
    }

    // D04：验证码正确，J4 为真
    @Test
    void testD04() {
        assertTrue(CaptchaService.verify(
                createValidKey(), "ABC123"));
    }

    // D05：验证码错误，J4 为假
    @Test
    void testD05() {
        assertFalse(CaptchaService.verify(
                createValidKey(), "XYZ789"));
    }
}
