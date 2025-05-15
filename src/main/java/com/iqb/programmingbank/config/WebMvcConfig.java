package com.iqb.programmingbank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web 相关配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 添加静态资源映射
     * 将本地的img目录映射到/img/**路径下访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本项目的img目录下的资源映射为/img/**
        String backendImgPath = "file:" + System.getProperty("user.dir") + File.separator + "img" + File.separator;
        registry.addResourceHandler("/img/**")
                .addResourceLocations(backendImgPath)
                .setCachePeriod(3600) // 缓存一小时
                .resourceChain(true);  // 开启资源链优化
        
        // 尝试添加前端项目的静态资源目录映射
        String frontendPath = System.getProperty("user.dir") + File.separator + ".." + File.separator + "IQB_programmingBank_frontend";
        String[] possibleDirs = {"public", "static", "dist"};
        
        for (String dir : possibleDirs) {
            File frontendDir = new File(frontendPath + File.separator + dir);
            if (frontendDir.exists() && frontendDir.isDirectory()) {
                String frontendResourcePath = "file:" + frontendDir.getAbsolutePath() + File.separator;
                // 注册前端静态资源目录
                registry.addResourceHandler("/**")
                        .addResourceLocations(frontendResourcePath)
                        .setCachePeriod(3600)
                        .resourceChain(true);
                break;
            }
        }
    }
} 