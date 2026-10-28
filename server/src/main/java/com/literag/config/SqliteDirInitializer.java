package com.literag.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SQLite 不会像 H2 那样自动创建库文件的父目录，缺失时会直接报
 * "unable to open database file"。这里在容器启动前把目录补上。
 * 非 SQLite 数据源直接跳过。
 */
public class SqliteDirInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PREFIX = "jdbc:sqlite:";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        Path dbFile = resolve(env.getProperty("spring.datasource.url", ""));
        if (dbFile == null) {
            return;
        }
        Path dir = dbFile.toAbsolutePath().getParent();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 SQLite 数据目录：" + dir, e);
        }
    }

    private Path resolve(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return null;
        }
        String path = url.substring(PREFIX.length());
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (path.isBlank() || path.startsWith(":memory:") || path.startsWith("file::memory:")) {
            return null;
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        // Windows 上可能出现 /C:/xxx 形式
        if (path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return Paths.get(path);
    }
}
