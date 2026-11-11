package com.moviehub.server.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 运维后台的会话登录。
 *
 * <p>用户名 + 密码校验通过后，签发一个随机会话令牌写入 HttpOnly Cookie，后续写接口凭
 * Cookie 鉴权，页面不再需要手工输入密钥。会话保存在内存中，服务重启后失效。
 *
 * <p>密码只以环境变量 {@code moviehub.admin-password} 提供；未配置时登录整体禁用，
 * 避免公开部署出现默认口令。
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    public static final String COOKIE_NAME = "mh_admin";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String username;
    private final String password;
    private final Duration ttl;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(
            @Value("${moviehub.admin-user:admin}") String username,
            @Value("${moviehub.admin-password:}") String password,
            @Value("${moviehub.admin-session-hours:12}") long sessionHours) {
        this.username = username == null ? "admin" : username.trim();
        this.password = password == null ? "" : password;
        this.ttl = Duration.ofHours(Math.max(1, sessionHours));
    }

    public boolean isConfigured() {
        return !password.isEmpty();
    }

    /** 校验用户名密码；成功后签发新会话并写入 Cookie。 */
    public boolean login(String user, String pass, HttpServletRequest request, HttpServletResponse response) {
        if (!isConfigured()) {
            return false;
        }
        if (!constEq(username, user == null ? "" : user.trim())) {
            return false;
        }
        if (!constEq(password, pass == null ? "" : pass)) {
            return false;
        }
        pruneExpired();
        String token = newToken();
        sessions.put(token, Instant.now().plus(ttl));
        writeCookie(request, response, token, (int) ttl.toSeconds());
        log.info("运维后台登录成功: {}", user);
        return true;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request);
        if (token != null) {
            sessions.remove(token);
        }
        writeCookie(request, response, "", 0);
    }

    /** 当前请求是否持有有效会话。 */
    public boolean isAuthenticated(HttpServletRequest request) {
        if (!isConfigured()) {
            return false;
        }
        String token = readCookie(request);
        if (token == null) {
            return false;
        }
        Instant expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        // 滑动续期：剩余不足一半时自动延长
        if (Duration.between(Instant.now(), expiry).compareTo(ttl.dividedBy(2)) < 0) {
            sessions.put(token, Instant.now().plus(ttl));
        }
        return true;
    }

    public String currentUser(HttpServletRequest request) {
        return isAuthenticated(request) ? username : null;
    }

    public void pruneExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }

    /* ── Cookie ── */

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeCookie(HttpServletRequest request, HttpServletResponse response, String value, int maxAge) {
        boolean https = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))
                || "https".equalsIgnoreCase(request.getScheme());
        StringBuilder sb = new StringBuilder()
                .append(COOKIE_NAME).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAge);
        if (https) {
            sb.append("; Secure");
        }
        response.addHeader("Set-Cookie", sb.toString());
    }

    /* ── 工具 ── */

    private static String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** 定长比较，避免逐字符时序泄漏。 */
    private static boolean constEq(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
