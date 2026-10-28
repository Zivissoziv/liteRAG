package com.literag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.literag.config.AppProperties;
import com.literag.config.SqliteDirInitializer;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class LiteRagApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LiteRagApplication.class);
        // SQLite 要求库文件所在目录已存在，在环境就绪后、数据源初始化前补建
        app.addInitializers(new SqliteDirInitializer());
        app.run(args);
    }
}
