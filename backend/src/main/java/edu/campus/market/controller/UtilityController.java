package edu.campus.market.controller;

import edu.campus.market.common.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.Base64;

/**
 * 通用工具控制器
 * 提供文件上传、图片验证码生成接口
 */
@RestController
@RequestMapping("/api")
public class UtilityController {

    /** 验证码字符集，去掉容易混淆字符 0 O 1 I */
    private static final String CAPTCHA_CHAR_SET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 验证码字符个数 */
    private static final int CAPTCHA_CODE_COUNT = 4;
    /** 验证码图片宽度 */
    private static final int CAPTCHA_IMG_WIDTH = 140;
    /** 验证码图片高度 */
    private static final int CAPTCHA_IMG_HEIGHT = 48;
    /** 干扰线条数量 */
    private static final int CAPTCHA_INTERFERE_LINE_COUNT = 6;
    /** 干扰点数量 */
    private static final int CAPTCHA_INTERFERE_POINT_COUNT = 30;
    /** 验证码字体大小 */
    private static final int CAPTCHA_FONT_SIZE = 28;

    /** 干扰线颜色最大值 */
    private static final int LINE_COLOR_MAX = 180;
    /** 噪点颜色最大值 */
    private static final int POINT_COLOR_MAX = 200;
    /** 文字颜色基础上限 */
    private static final int TEXT_COLOR_BASE = 120;
    /** 文字颜色偏移增量 */
    private static final int TEXT_COLOR_OFFSET = 30;
    /** 噪点圆点大小 */
    private static final int POINT_SIZE = 2;
    /** 文字起始X坐标 */
    private static final int TEXT_START_X = 14;
    /** 文字Y坐标 */
    private static final int TEXT_Y_POS = 34;
    /** 单个文字横向间隔 */
    private static final int TEXT_X_STEP = 30;

    /** 上传文件存储目录 */
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 图片上传接口
     * @param file 上传的文件对象
     * @return ApiResult，返回访问url
     * @throws IOException 文件读写异常
     */
    @PostMapping("/upload")
    public ApiResult<?> upload(
            @RequestParam("file") final MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择文件");
        }

        String originalFilename = Optional.
                ofNullable(file.getOriginalFilename()).orElse("file");

        String ext = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "");
        String name = UUID.randomUUID() + "-" + ext;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        Files.copy(
                file.getInputStream(),
                dir.resolve(name),
                StandardCopyOption.REPLACE_EXISTING
        );

        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("url", "/uploads/" + name);
        return ApiResult.ok(resultMap);
    }

    /**
     * 图片验证码：生成4位随机字符，存入内存缓存（key=UUID，5分钟过期），
     * 返回 captchaKey（前端注册带回） + base64 图片。
     * @return ApiResult，包含captchaKey与base64图片
     * @throws IOException 图片流处理异常
     */
    @GetMapping("/captcha")
    public ApiResult<?> captcha() throws IOException {
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < CAPTCHA_CODE_COUNT; i++) {
            int index = random.nextInt(CAPTCHA_CHAR_SET.length());
            code.append(CAPTCHA_CHAR_SET.charAt(index));
        }
        String codeStr = code.toString();
        String captchaKey = CaptchaService.put(codeStr);

        BufferedImage img = new BufferedImage(
                CAPTCHA_IMG_WIDTH,
                CAPTCHA_IMG_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = img.createGraphics();

        // 填充白色背景
        graphics.setColor(Color.WHITE);
        graphics.fillRect(
                0,
                0,
                CAPTCHA_IMG_WIDTH,
                CAPTCHA_IMG_HEIGHT
        );

        // 绘制干扰线
        for (int i = 0; i < CAPTCHA_INTERFERE_LINE_COUNT; i++) {
            int r = random.nextInt(LINE_COLOR_MAX);
            int g = random.nextInt(LINE_COLOR_MAX);
            int b = random.nextInt(LINE_COLOR_MAX);
            graphics.setColor(new Color(r, g, b));

            int x1 = random.nextInt(CAPTCHA_IMG_WIDTH);
            int y1 = random.nextInt(CAPTCHA_IMG_HEIGHT);
            int x2 = random.nextInt(CAPTCHA_IMG_WIDTH);
            int y2 = random.nextInt(CAPTCHA_IMG_HEIGHT);
            graphics.drawLine(x1, y1, x2, y2);
        }

        // 绘制干扰噪点
        for (int i = 0; i < CAPTCHA_INTERFERE_POINT_COUNT; i++) {
            int r = random.nextInt(POINT_COLOR_MAX);
            int g = random.nextInt(POINT_COLOR_MAX);
            int b = random.nextInt(POINT_COLOR_MAX);
            graphics.setColor(new Color(r, g, b));

            int x = random.nextInt(CAPTCHA_IMG_WIDTH);
            int y = random.nextInt(CAPTCHA_IMG_HEIGHT);
            graphics.fillOval(x, y, POINT_SIZE, POINT_SIZE);
        }

        // 绘制验证码文字
        graphics.setFont(new Font("Arial", Font.BOLD, CAPTCHA_FONT_SIZE));
        for (int i = 0; i < CAPTCHA_CODE_COUNT; i++) {
            int r = random.nextInt(TEXT_COLOR_BASE) + TEXT_COLOR_OFFSET;
            int g = random.nextInt(TEXT_COLOR_BASE) + TEXT_COLOR_OFFSET;
            int b = random.nextInt(TEXT_COLOR_BASE) + TEXT_COLOR_OFFSET;
            graphics.setColor(new Color(r, g, b));

            int drawX = TEXT_START_X + i * TEXT_X_STEP;
            int drawY = TEXT_Y_POS;
            graphics.drawString(
                    codeStr.substring(i, i + 1),
                    drawX,
                    drawY
            );
        }
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        String imageBase64 = Base64.getEncoder().
                encodeToString(out.toByteArray());

        Map<String, String> returnMap = new HashMap<>();
        returnMap.put("captchaKey", captchaKey);
        returnMap.put("image",
                "data:image/png;base64," + imageBase64);

        return ApiResult.ok(returnMap);
    }
}
