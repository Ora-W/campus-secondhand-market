
package edu.campus.market.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConditionCoverageTest {

    // 生成有效验证码
    private String createValidKey() {
        return CaptchaService.put("ABC123");
    }

    // 生成不存在的 key
    private String createNotExistKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 生成过期验证码
    private String createExpiredKey() throws Exception {
        String key = CaptchaService.put("ABC123");

        Field field = CaptchaService.class.getDeclaredField("STORE");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> store =
                (Map<String, String>) field.get(null);

        // 设置为 301 秒前，模拟验证码过期
        long expiredTime = System.currentTimeMillis() - 301_000;
        store.put(key, "ABC123|" + expiredTime);

        return key;
    }

    // C01：A 为真，key 为空
    @Test
    void testC01() {
        assertFalse(CaptchaService.verify(null, "ABC123"));
    }

    // C02：B 为真，input 为空
    @Test
    void testC02() {
        assertFalse(CaptchaService.verify(
                createValidKey(), null));
    }

    // C03：J2 为真，缓存中不存在 key
    @Test
    void testC03() {
        assertFalse(CaptchaService.verify(
                createNotExistKey(), "ABC123"));
    }

    // C04：J3 为假、J4 为真，验证码正确且未过期
    @Test
    void testC04() {
        assertTrue(CaptchaService.verify(
                createValidKey(), "ABC123"));
    }

    // C05：J3 为假、J4 为假，验证码内容错误
    @Test
    void testC05() {
        assertFalse(CaptchaService.verify(
                createValidKey(), "XYZ789"));
    }

    // C06：J3 为真，验证码已经过期
    @Test
    void testC06() throws Exception {
        assertFalse(CaptchaService.verify(
                createExpiredKey(), "ABC123"));
    }
}
