package com.literag.config;

import com.literag.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(props.getCors().getAllowedOrigins().split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Mcp-Session-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 若外部存在前端构建产物（默认 ../web/dist），直接由后端托管，
     * 实现「一个 jar + 一个端口」部署；打包时也会复制一份到 classpath:/static。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(props.getWebDir()).toAbsolutePath().normalize();
        boolean external = Files.isDirectory(dir);
        var reg = registry.addResourceHandler("/**");
        if (external) {
            // 外部目录优先，便于前端重新构建后无需重新打包
            reg.addResourceLocations(dir.toUri().toString());
        }
        reg.addResourceLocations("classpath:/static/").setCachePeriod(3600);
    }

    /**
     * SPA 路由回退：/login、/kb/1 等前端路径刷新时转发到 index.html。
     * 只匹配不含点号的一至三段路径，避免吞掉 /api/**、静态资源（*.js/*.png）与 /mcp。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{a:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/{a:[^.]+}/{b:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/{a:[^.]+}/{b:[^.]+}/{c:[^.]+}").setViewName("forward:/index.html");
    }

    @Bean("ingestExecutor")
    public Executor ingestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
