
package edu.campus.market.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DecisionConditionCoverageTest {

    // 生成有效验证码
    private String createValidKey() {
        return CaptchaService.put("ABC123");
    }

    // 生成不存在的 key
    private String createNotExistKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 生成已经过期的验证码
    private String createExpiredKey() throws Exception {
        String key = CaptchaService.put("ABC123");

        Field field = CaptchaService.class.getDeclaredField("STORE");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> store =
                (Map<String, String>) field.get(null);

        long expiredTime = System.currentTimeMillis() - 301_000;
        store.put(key, "ABC123|" + expiredTime);

        return key;
    }

    // DC01：key 为空，J1 为真
    @Test
    void testDC01() {
        assertFalse(CaptchaService.verify(null, "ABC123"));
    }

    // DC02：input 为空，J1 为真
    @Test
    void testDC02() {
        assertFalse(CaptchaService.verify(
                createValidKey(), null));
    }

    // DC03：key 和 input 都不为空，但缓存不存在，J2 为真
    @Test
    void testDC03() {
        assertFalse(CaptchaService.verify(
                createNotExistKey(), "ABC123"));
    }

    // DC04：验证码过期，J3 为真
    @Test
    void testDC04() throws Exception {
        assertFalse(CaptchaService.verify(
                createExpiredKey(), "ABC123"));
    }

    // DC05：验证码正确，J4 为真
    @Test
    void testDC05() {
        assertTrue(CaptchaService.verify(
                createValidKey(), "ABC123"));
    }

    // DC06：验证码错误，J4 为假
    @Test
    void testDC06() {
        assertFalse(CaptchaService.verify(
                createValidKey(), "XYZ789"));
    }
}
