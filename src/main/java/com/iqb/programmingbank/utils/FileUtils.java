package com.iqb.programmingbank.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件工具类
 */
public class FileUtils {

    /**
     * 允许的图片后缀
     */
    private static final List<String> ALLOW_IMAGE_SUFFIX = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    );

    /**
     * 最大文件大小（5MB）
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 验证是否为图片
     *
     * @param file 文件对象
     * @return 是否为图片
     */
    public static boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            return false;
        }
        
        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        return ALLOW_IMAGE_SUFFIX.contains(suffix);
    }

    /**
     * 验证文件大小
     *
     * @param file 文件对象
     * @return 是否超过限制
     */
    public static boolean exceedsMaxSize(MultipartFile file) {
        return file.getSize() > MAX_FILE_SIZE;
    }

    /**
     * 查找前端项目的静态资源目录
     *
     * @return 前端静态资源目录的完整路径，如果找不到则返回null
     */
    public static String findFrontendStaticDir() {
        // 默认情况，前端项目和后端项目在同一目录下
        String baseDir = System.getProperty("user.dir");
        File parentDir = new File(baseDir).getParentFile();
        
        if (parentDir != null && parentDir.exists()) {
            // 尝试多种可能的前端项目目录名
            String[] possibleProjectNames = {
                " IQB_programmingBank_frontend",
                "IQB_programmingBank_frontend",
                "IQB_programmingBank-web",
                "IQB_programmingBank-ui",
                "frontend",
                "web",
                "client"
            };
            
            // 尝试多种可能的静态资源目录名
            String[] possibleStaticDirs = {
                "public", 
                "static", 
                "dist", 
                "build", 
                "www", 
                "assets"
            };
            
            // 先尝试查找前端项目目录
            for (String projectName : possibleProjectNames) {
                File projectDir = new File(parentDir, projectName);
                if (projectDir.exists() && projectDir.isDirectory()) {
                    // 然后查找静态资源目录
                    for (String staticDir : possibleStaticDirs) {
                        File staticDirFile = new File(projectDir, staticDir);
                        if (staticDirFile.exists() && staticDirFile.isDirectory()) {
                            return staticDirFile.getAbsolutePath();
                        }
                    }
                }
            }
            
            // 如果找不到前端项目，尝试在当前项目的同级目录中查找静态资源目录
            for (String staticDir : possibleStaticDirs) {
                File staticDirFile = new File(parentDir, staticDir);
                if (staticDirFile.exists() && staticDirFile.isDirectory()) {
                    return staticDirFile.getAbsolutePath();
                }
            }
        }
        
        // 最后尝试在当前项目目录下查找
        String[] possibleStaticDirs = {"public", "static", "dist", "resources/static"};
        for (String staticDir : possibleStaticDirs) {
            File staticDirFile = new File(baseDir, staticDir);
            if (staticDirFile.exists() && staticDirFile.isDirectory()) {
                return staticDirFile.getAbsolutePath();
            }
        }
        
        return null;
    }
    
    /**
     * 保存问题库图片
     * 
     * @param imageUrl 原始图片URL
     * @param title 题库标题 (用于创建文件夹)
     * @return 新的图片相对路径
     * @throws IOException 如果保存图片发生错误
     */
    public static String saveQuestionBankImage(String imageUrl, String title) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty() || title == null || title.trim().isEmpty()) {
            return null;
        }
        
        // 获取图片文件名和后缀
        String originalFileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        String suffix = originalFileName.contains(".") ? 
                originalFileName.substring(originalFileName.lastIndexOf(".")) : ".jpg";
        
        // 验证文件类型
        if (!ALLOW_IMAGE_SUFFIX.contains(suffix.toLowerCase())) {
            throw new IOException("unsupported image format: " + suffix);
        }
        
        // 处理题库标题，替换不允许作为文件夹名的字符
        String safeFolderName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // 生成新的文件名
        String newFileName = UUID.randomUUID().toString() + suffix;
        
        try {
            // 1. 保存到后端项目目录
            String backendDir = System.getProperty("user.dir") + File.separator + "img" 
                             + File.separator + "questionBank" + File.separator + safeFolderName;
            Path backendPath = Paths.get(backendDir);
            if (!Files.exists(backendPath)) {
                Files.createDirectories(backendPath);
            }
            Path backendFilePath = backendPath.resolve(newFileName);
            
            // 下载图片
            URL url = new URL(imageUrl);
            Files.copy(url.openStream(), backendFilePath, StandardCopyOption.REPLACE_EXISTING);
            
            // 2. 同时保存到前端项目的静态资源目录（如果存在）
            String frontendStaticDir = findFrontendStaticDir();
            if (frontendStaticDir != null) {
                String frontendImgDir = frontendStaticDir + File.separator + "img" 
                                     + File.separator + "questionBank" + File.separator + safeFolderName;
                Path frontendPath = Paths.get(frontendImgDir);
                if (!Files.exists(frontendPath)) {
                    Files.createDirectories(frontendPath);
                }
                Path frontendFilePath = frontendPath.resolve(newFileName);
                // 复制文件到前端目录
                Files.copy(backendFilePath, frontendFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 生成可访问的URL
            return "/img/questionBank/" + safeFolderName + "/" + newFileName;
        } catch (IOException e) {
            throw new IOException("image save failed: " + e.getMessage(), e);
        }
    }
} 