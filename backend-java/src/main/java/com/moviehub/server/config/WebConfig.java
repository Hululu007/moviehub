package com.moviehub.server.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 允许 source-hub 运维页与调试页跨域读取只读接口。
 * 允许来源由 {@code moviehub.allowed-origins} 控制，逗号分隔，{@code *} 表示全部。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${moviehub.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        // 登录会话依赖 Cookie，allowCredentials 为 true 时不能用 "*"，
        // 必须用 allowedOriginPatterns（它会为每个来源回显具体 Origin）
        registry.addMapping("/**")
                .allowedOriginPatterns(origins.isEmpty() ? new String[] {"*"} : origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
