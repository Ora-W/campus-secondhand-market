
package edu.campus.market.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConditionCombinationCoverageTest {

    // 生成有效验证码
    private String createValidKey() {
        return CaptchaService.put("ABC123");
    }

    // 生成缓存中不存在的 key
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

        // 设置为 301 秒前，模拟验证码过期
        long expiredTime = System.currentTimeMillis() - 301_000;
        store.put(key, "ABC123|" + expiredTime);

        return key;
    }

    // CC01：key 为空，input 为空
    @Test
    void testCC01() {
        assertFalse(CaptchaService.verify(null, null));
    }

    // CC02：key 为空，input 不为空
    @Test
    void testCC02() {
        assertFalse(CaptchaService.verify(null, "ABC123"));
    }

    // CC03：key 不为空，input 为空
    @Test
    void testCC03() {
        assertFalse(CaptchaService.verify(
                createValidKey(), null));
    }

    // CC04：key 不为空，input 不为空，验证码正确
    @Test
    void testCC04() {
        assertTrue(CaptchaService.verify(
                createValidKey(), "ABC123"));
    }

    // CC05：key 不为空，但缓存中不存在
    @Test
    void testCC05() {
        assertFalse(CaptchaService.verify(
                createNotExistKey(), "ABC123"));
    }

    // CC06：验证码已经过期
    @Test
    void testCC06() throws Exception {
        assertFalse(CaptchaService.verify(
                createExpiredKey(), "ABC123"));
    }

    // CC07：验证码内容不匹配
    @Test
    void testCC07() {
        assertFalse(CaptchaService.verify(
                createValidKey(), "XYZ789"));
    }
}
