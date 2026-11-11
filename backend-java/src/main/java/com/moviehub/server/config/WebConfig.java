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
        registry.addMapping("/**")
                .allowedOrigins(origins.isEmpty() ? new String[] {"*"} : origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Admin-Key")
                .maxAge(3600);
    }
}
