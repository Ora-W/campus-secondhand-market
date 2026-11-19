package edu.campus.market.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码内存缓存（单机版，5分钟过期）。
 * 生产环境建议用 Redis。
 */
public class CaptchaService {

    private static final long EXPIRE_SECONDS = 5 * 60L;

    /** key = captchaKey(UUID), value = code + "|" + 时间戳 */
    private static final Map<String, String> STORE = new ConcurrentHashMap<>();

    /** 生成一个新的验证码，保存并返回 key */
    public static String put(String code) {
        String key = java.util.UUID.randomUUID().toString().replace("-", "");
        STORE.put(key, code.toUpperCase() + "|" + System.currentTimeMillis());
        return key;
    }

    /** 校验：比对后无论是否成功都立即作废（一次性） */
    public static boolean verify(String key, String input) {
        if (key == null || input == null) return false;
        String raw = STORE.remove(key);
        if (raw == null) return false;
        String[] parts = raw.split("\\|");
        long ts = Long.parseLong(parts[1]);
        if (System.currentTimeMillis() - ts > EXPIRE_SECONDS * 1000) return false;
        return parts[0].equalsIgnoreCase(input);
    }

    /** 清理过期项（可选，定时调用） */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        STORE.entrySet().removeIf(e -> {
            long ts = Long.parseLong(e.getValue().split("\\|")[1]);
            return now - ts > EXPIRE_SECONDS * 1000;
        });
    }
}
