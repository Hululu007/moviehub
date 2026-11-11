package com.moviehub.server.api;

import com.moviehub.server.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台登录/登出。会话通过 HttpOnly Cookie 维持，页面不再需要手工粘贴密钥。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminAuthService auth;

    public AuthController(AdminAuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(String username, String password) {
    }

    /** 当前登录状态；页面加载时调用，决定显示登录框还是控制台。 */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        boolean authenticated = auth.isAuthenticated(request);
        return Map.of(
                "authenticated", authenticated,
                "configured", auth.isConfigured(),
                "user", authenticated ? auth.currentUser(request) : "");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (!auth.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "服务端未配置 ADMIN_PASSWORD，登录功能已禁用"));
        }
        boolean ok = body != null
                && auth.login(body.username(), body.password(), request, response);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "error", "用户名或密码不正确"));
        }
        // 登录成功的 Cookie 只写在 response 上，本次 request 里还没有它，
        // 所以不能回读 currentUser（会拿到 null 并被 Map.of 拒绝）。
        String user = body.username() == null ? "" : body.username().trim();
        return ResponseEntity.ok(Map.of("ok", true, "user", user));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
        return Map.of("ok", true);
    }
}
