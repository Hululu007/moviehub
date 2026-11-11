package com.moviehub.server.config;

import com.moviehub.server.dto.LiveChannelDto;
import com.moviehub.server.dto.MusicScriptDto;
import com.moviehub.server.dto.VodSourceDto;
import com.moviehub.server.service.SourceQueryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时建表并在空表时写入默认源。
 *
 * <p>默认数据直接取自 Java 常量而非 {@code data.sql}，避免不同平台默认编码把中文读成乱码。
 * 任何异常只记录日志，不阻断启动——数据库不可用时接口会回退到内置默认源。
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS vod_source ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "source_key VARCHAR(64) NOT NULL UNIQUE,"
            + "name VARCHAR(128) NOT NULL,"
            + "api_url VARCHAR(512) NOT NULL,"
            + "enabled TINYINT(1) NOT NULL DEFAULT 1,"
            + "sort_order INT NOT NULL DEFAULT 100,"
            + "latency_ms INT NOT NULL DEFAULT 0,"
            + "health_status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',"
            + "last_checked_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
        "CREATE TABLE IF NOT EXISTS live_channel ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "channel_code VARCHAR(64) NOT NULL UNIQUE,"
            + "name VARCHAR(128) NOT NULL,"
            + "group_name VARCHAR(64) NOT NULL DEFAULT '央视频道',"
            + "stream_url VARCHAR(1024) NOT NULL,"
            + "enabled TINYINT(1) NOT NULL DEFAULT 1,"
            + "sort_order INT NOT NULL DEFAULT 100,"
            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
        "CREATE TABLE IF NOT EXISTS music_script ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "script_key VARCHAR(64) NOT NULL UNIQUE,"
            + "name VARCHAR(128) NOT NULL,"
            + "script_url VARCHAR(1024) NOT NULL,"
            + "enabled TINYINT(1) NOT NULL DEFAULT 1,"
            + "sort_order INT NOT NULL DEFAULT 100,"
            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
        "CREATE TABLE IF NOT EXISTS app_meta ("
            + "meta_key VARCHAR(64) PRIMARY KEY,"
            + "meta_value VARCHAR(512) NOT NULL,"
            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    };

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String ddl : DDL) {
                jdbc.execute(ddl);
            }
            seedIfEmpty();
            log.info("数据库初始化完成");
        } catch (Exception e) {
            log.warn("数据库初始化跳过（将在接口层回退内置默认源）: {}", e.getMessage());
        }
    }

    /** 仅当对应表为空时写入默认值，不覆盖运维在后台改过的数据。 */
    private void seedIfEmpty() {
        if (count("vod_source") == 0) {
            List<VodSourceDto> vod = SourceQueryService.DefaultSources.vod();
            for (int i = 0; i < vod.size(); i++) {
                VodSourceDto s = vod.get(i);
                jdbc.update(
                        "INSERT IGNORE INTO vod_source (source_key, name, api_url, enabled, sort_order) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        s.id(), s.name(), s.api(), s.enabled(), (i + 1) * 10);
            }
            log.info("已写入 {} 个默认片源", vod.size());
        }
        if (count("live_channel") == 0) {
            List<LiveChannelDto> live = SourceQueryService.DefaultSources.live();
            for (int i = 0; i < live.size(); i++) {
                LiveChannelDto c = live.get(i);
                jdbc.update(
                        "INSERT IGNORE INTO live_channel "
                                + "(channel_code, name, group_name, stream_url, enabled, sort_order) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        c.id(), c.name(), c.group(), c.url(), c.enabled(), (i + 1) * 10);
            }
            log.info("已写入 {} 个默认直播频道", live.size());
        }
        if (count("music_script") == 0) {
            List<MusicScriptDto> scripts = SourceQueryService.DefaultSources.scripts();
            for (int i = 0; i < scripts.size(); i++) {
                MusicScriptDto m = scripts.get(i);
                jdbc.update(
                        "INSERT IGNORE INTO music_script (script_key, name, script_url, enabled, sort_order) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        m.id(), m.name(), m.url(), m.enabled(), (i + 1) * 10);
            }
            log.info("已写入 {} 个默认音源脚本", scripts.size());
        }
        jdbc.update(
                "INSERT IGNORE INTO app_meta (meta_key, meta_value) VALUES ('bundle_version', ?)",
                SourceQueryService.DefaultSources.DEFAULT_VERSION);
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
