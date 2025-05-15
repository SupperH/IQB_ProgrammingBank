package com.iqb.programmingbank.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iqb.programmingbank.common.BaseResponse;
import com.iqb.programmingbank.common.DeleteRequest;
import com.iqb.programmingbank.common.ErrorCode;
import com.iqb.programmingbank.common.ResultUtils;
import com.iqb.programmingbank.exception.BusinessException;
import com.iqb.programmingbank.exception.ThrowUtils;
import com.iqb.programmingbank.model.dto.question.QuestionQueryRequest;
import com.iqb.programmingbank.model.dto.questionBank.QuestionBankAddRequest;
import com.iqb.programmingbank.model.dto.questionBank.QuestionBankEditRequest;
import com.iqb.programmingbank.model.dto.questionBank.QuestionBankQueryRequest;
import com.iqb.programmingbank.model.dto.questionBank.QuestionBankUpdateRequest;
import com.iqb.programmingbank.model.entity.Question;
import com.iqb.programmingbank.model.entity.QuestionBank;
import com.iqb.programmingbank.model.entity.User;
import com.iqb.programmingbank.model.vo.QuestionBankVO;
import com.iqb.programmingbank.model.vo.QuestionVO;
import com.iqb.programmingbank.sentinel.SentinelConstant;
import com.iqb.programmingbank.service.QuestionBankService;
import com.iqb.programmingbank.service.QuestionService;
import com.iqb.programmingbank.service.UserService;
import com.iqb.programmingbank.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 题库接口
 *
 * @author zeden
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    // region 增删改查

    /**
     * 创建题库
     *
     * @param file 题库图片文件
     * @param questionBankAddRequest 题库信息
     * @param request HTTP请求
     * @return 新创建的题库ID
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestionBank(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "questionBankAddRequest") String questionBankAddRequestJson,
            HttpServletRequest request) {
        QuestionBankAddRequest questionBankAddRequest = JSON.parseObject(questionBankAddRequestJson, QuestionBankAddRequest.class);

        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 转换DTO为实体类
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankAddRequest, questionBank);

        // 判断题库是否已经存在
        Long count = questionBankService.query().eq("title", questionBank.getTitle()).count();
        ThrowUtils.throwIf(count!=0, ErrorCode.EXISITING_ERROR);

        // 处理题库图片
        if (file != null && !file.isEmpty()) {
            // 验证文件
            if (!FileUtils.isImage(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file type error");
            }
            
            if (FileUtils.exceedsMaxSize(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file size cannot exceed 5MB");
            }
            
            try {
                // 处理题库标题，替换不允许作为文件夹名的字符
                String safeFolderName = questionBank.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                
                // 获取文件后缀
                String fileName = file.getOriginalFilename();
                String suffix = fileName != null && fileName.contains(".") 
                    ? fileName.substring(fileName.lastIndexOf(".")) 
                    : ".jpg";
                
                // 生成新的文件名
                String newFileName = UUID.randomUUID().toString() + suffix;
                
                // 1. 保存到后端项目目录
                String backendDir = System.getProperty("user.dir") + File.separator + "img" 
                                 + File.separator + "questionBank" + File.separator + safeFolderName;
                Path backendPath = Paths.get(backendDir);
                if (!Files.exists(backendPath)) {
                    Files.createDirectories(backendPath);
                }
                Path backendFilePath = backendPath.resolve(newFileName);
                Files.copy(file.getInputStream(), backendFilePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("question bank image saved to backend directory: {}", backendFilePath);
                
                // 2. 同时保存到前端项目的静态资源目录（如果存在）
                String frontendStaticDir = FileUtils.findFrontendStaticDir();
                if (frontendStaticDir != null) {
                    String frontendImgDir = frontendStaticDir + File.separator + "img" 
                                         + File.separator + "questionBank" + File.separator + safeFolderName;
                    Path frontendPath = Paths.get(frontendImgDir);
                    if (!Files.exists(frontendPath)) {
                        Files.createDirectories(frontendPath);
                    }
                    Path frontendFilePath = frontendPath.resolve(newFileName);
                    // 重新获取输入流，因为上面的copy操作已经消耗了输入流
                    Files.copy(file.getInputStream(), frontendFilePath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("question bank image saved to frontend static resource directory: {}", frontendFilePath);
                } else {
                    log.warn("frontend static resource directory not found, question bank image saved to backend");
                }
                
                // 生成可访问的URL
                String imageUrl = "/img/questionBank/" + safeFolderName + "/" + newFileName;
                
                // 更新题库的图片地址
                questionBank.setPicture(imageUrl);
            } catch (IOException e) {
                log.error("question bank image save failed: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "image save failed: " + e.getMessage());
            }
        } else if (questionBank.getPicture() != null && !questionBank.getPicture().isEmpty() 
                && !questionBank.getPicture().startsWith("/img/")) {
            // 如果没有上传文件但提供了图片URL，尝试下载图片（保持原逻辑）
            try {
                String newImagePath = FileUtils.saveQuestionBankImage(
                        questionBank.getPicture(), questionBank.getTitle());
                questionBank.setPicture(newImagePath);
                log.info("question bank image saved: {}", newImagePath);
            } catch (IOException e) {
                log.error("question bank image save failed", e);
            }
        }

        // 数据校验
        questionBankService.validQuestionBank(questionBank, true);
        
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        questionBank.setUserId(loginUser.getId());
        
        // 写入数据库
        boolean result = questionBankService.save(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        // 返回新写入的数据id
        long newQuestionBankId = questionBank.getId();
        return ResultUtils.success(newQuestionBankId);
    }

    /**
     * 删除题库
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestionBank.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题库（管理员功能）
     *
     * @param file 题库图片文件
     * @param questionBankUpdateRequest 题库更新请求
     * @return 是否成功
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateQuestionBank(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        if (questionBankUpdateRequest == null || questionBankUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 转换DTO为实体类
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankUpdateRequest, questionBank);
        
        // 判断是否存在
        long id = questionBankUpdateRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 处理题库图片
        if (file != null && !file.isEmpty()) {
            // 验证文件
            if (!FileUtils.isImage(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file type error");
            }
            
            if (FileUtils.exceedsMaxSize(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file size cannot exceed 5MB");
            }
            
            try {
                // 处理题库标题，替换不允许作为文件夹名的字符
                String safeFolderName = oldQuestionBank.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                
                // 获取文件后缀
                String fileName = file.getOriginalFilename();
                String suffix = fileName != null && fileName.contains(".") 
                    ? fileName.substring(fileName.lastIndexOf(".")) 
                    : ".jpg";
                
                // 生成新的文件名
                String newFileName = UUID.randomUUID().toString() + suffix;
                
                // 1. 保存到后端项目目录
                String backendDir = System.getProperty("user.dir") + File.separator + "img" 
                                 + File.separator + "questionBank" + File.separator + safeFolderName;
                Path backendPath = Paths.get(backendDir);
                if (!Files.exists(backendPath)) {
                    Files.createDirectories(backendPath);
                }
                Path backendFilePath = backendPath.resolve(newFileName);
                Files.copy(file.getInputStream(), backendFilePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("question bank image saved to backend directory: {}", backendFilePath);
                
                // 2. 同时保存到前端项目的静态资源目录（如果存在）
                String frontendStaticDir = FileUtils.findFrontendStaticDir();
                if (frontendStaticDir != null) {
                    String frontendImgDir = frontendStaticDir + File.separator + "img" 
                                         + File.separator + "questionBank" + File.separator + safeFolderName;
                    Path frontendPath = Paths.get(frontendImgDir);
                    if (!Files.exists(frontendPath)) {
                        Files.createDirectories(frontendPath);
                    }
                    Path frontendFilePath = frontendPath.resolve(newFileName);
                    // 重新获取输入流，因为上面的copy操作已经消耗了输入流
                    Files.copy(file.getInputStream(), frontendFilePath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("question bank image saved to frontend static resource directory: {}", frontendFilePath);
                } else {
                    log.warn("frontend static resource directory not found, question bank image saved to backend");
                }
                
                // 生成可访问的URL
                String imageUrl = "/img/questionBank/" + safeFolderName + "/" + newFileName;
                
                // 更新题库的图片地址
                questionBank.setPicture(imageUrl);
            } catch (IOException e) {
                log.error("question bank image save failed: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "image save failed: " + e.getMessage());
            }
        } else if (questionBank.getPicture() != null && !questionBank.getPicture().isEmpty() 
                && !questionBank.getPicture().startsWith("/img/")) {
            // 如果没有上传文件但提供了图片URL，尝试下载图片（保持原逻辑）
            try {
                String newImagePath = FileUtils.saveQuestionBankImage(
                        questionBank.getPicture(), oldQuestionBank.getTitle());
                questionBank.setPicture(newImagePath);
                log.info("question bank image updated: {}", newImagePath);
            } catch (IOException e) {
                log.error("question bank image update failed", e);
            }
        }
        
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题库（封装类）
     *
     * @param questionBankQueryRequest
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQueryRequest.getId();
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // todo 取消注释开启 HotKey（须确保 HotKey 依赖被打进 jar 包）
//        // 生成 key
//        String key = "bank_detail_" + id;
//        // 如果是热 key
//        if (JdHotKeyStore.isHotKey(key)) {
//            // 从本地缓存中获取缓存值
//            Object cachedQuestionBankVO = JdHotKeyStore.get(key);
//            if (cachedQuestionBankVO != null) {
//                // 如果缓存中有值，直接返回缓存的值
//                return ResultUtils.success((QuestionBankVO) cachedQuestionBankVO);
//            }
//        }

        // 查询数据库
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 查询题库封装类
        QuestionBankVO questionBankVO = questionBankService.getQuestionBankVO(questionBank, request);
        // 是否要关联查询题库下的题目列表
        boolean needQueryQuestionList = questionBankQueryRequest.isNeedQueryQuestionList();
        if (needQueryQuestionList) {
            QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
            questionQueryRequest.setQuestionBankId(id);
            // 可以按需支持更多的题目搜索参数，比如分页
            questionQueryRequest.setPageSize(questionBankQueryRequest.getPageSize());
            questionQueryRequest.setCurrent(questionBankQueryRequest.getCurrent());
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
            Page<QuestionVO> questionVOPage = questionService.getQuestionVOPage(questionPage, request);
            questionBankVO.setQuestionPage(questionVOPage);
        }

        // todo 取消注释开启 HotKey（须确保 HotKey 依赖被打进 jar 包）
//        // 设置本地缓存（如果不是热 key，这个方法不会设置缓存）
//        JdHotKeyStore.smartSet(key, questionBankVO);

        // 获取封装类
        return ResultUtils.success(questionBankVO);
    }

    /**
     * 分页获取题库列表
     *
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // get question bank
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankPage);
    }

    /**
     * 分页获取题库列表（封装类）
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @SentinelResource(value = SentinelConstant.listQuestionBankVOByPage,
            blockHandler = "handleBlockException",
            fallback = "handleFallback")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                       HttpServletRequest request) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * listQuestionBankVOByPage 流控操作（此处为了方便演示，写在同一个类中）
     * 限流：提示"系统压力过大，请耐心等待"
     * 熔断：执行降级操作
     */
    public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                   HttpServletRequest request, BlockException ex) {
        // 降级操作
        if (ex instanceof DegradeException) {
            return handleFallback(questionBankQueryRequest, request, ex);
        }
        // 限流操作
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "system under pressure, please wait");
    }

    /**
     * listQuestionBankVOByPage 降级操作：直接返回本地数据（此处为了方便演示，写在同一个类中）
     */
    public BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }

    /**
     * 分页获取当前登录用户创建的题库列表
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionBankQueryRequest.setUserId(loginUser.getId());
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * 编辑题库
     *
     * @param file 题库图片文件
     * @param questionBankEditRequest 题库编辑请求
     * @param request HTTP请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBank(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody QuestionBankEditRequest questionBankEditRequest, 
            HttpServletRequest request) {
        if (questionBankEditRequest == null || questionBankEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankEditRequest, questionBank);
        
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        User loginUser = userService.getLoginUser(request);
        long id = questionBankEditRequest.getId();
        
        // 判断是否存在
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 仅本人或管理员可编辑
        if (!oldQuestionBank.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        
        // 处理题库图片
        if (file != null && !file.isEmpty()) {
            // 验证文件
            if (!FileUtils.isImage(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file type error");
            }
            
            if (FileUtils.exceedsMaxSize(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "file size cannot exceed 5MB");
            }
            
            try {
                // 处理题库标题，替换不允许作为文件夹名的字符
                String safeFolderName = oldQuestionBank.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                
                // 获取文件后缀
                String fileName = file.getOriginalFilename();
                String suffix = fileName != null && fileName.contains(".") 
                    ? fileName.substring(fileName.lastIndexOf(".")) 
                    : ".jpg";
                
                // 生成新的文件名
                String newFileName = UUID.randomUUID().toString() + suffix;
                
                // 1. 保存到后端项目目录
                String backendDir = System.getProperty("user.dir") + File.separator + "img" 
                                 + File.separator + "questionBank" + File.separator + safeFolderName;
                Path backendPath = Paths.get(backendDir);
                if (!Files.exists(backendPath)) {
                    Files.createDirectories(backendPath);
                }
                Path backendFilePath = backendPath.resolve(newFileName);
                Files.copy(file.getInputStream(), backendFilePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("question bank image saved to backend directory: {}", backendFilePath);
                
                // 2. 同时保存到前端项目的静态资源目录（如果存在）
                String frontendStaticDir = FileUtils.findFrontendStaticDir();
                if (frontendStaticDir != null) {
                    String frontendImgDir = frontendStaticDir + File.separator + "img" 
                                         + File.separator + "questionBank" + File.separator + safeFolderName;
                    Path frontendPath = Paths.get(frontendImgDir);
                    if (!Files.exists(frontendPath)) {
                        Files.createDirectories(frontendPath);
                    }
                    Path frontendFilePath = frontendPath.resolve(newFileName);
                    // 重新获取输入流，因为上面的copy操作已经消耗了输入流
                    Files.copy(file.getInputStream(), frontendFilePath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("question bank image saved to frontend static resource directory: {}", frontendFilePath);
                } else {
                    log.warn("frontend static resource directory not found, question bank image saved to backend");
                }
                
                // 生成可访问的URL
                String imageUrl = "/img/questionBank/" + safeFolderName + "/" + newFileName;
                
                // 更新题库的图片地址
                questionBank.setPicture(imageUrl);
            } catch (IOException e) {
                log.error("question bank image save failed: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "image save failed: " + e.getMessage());
            }
        } else if (questionBank.getPicture() != null && !questionBank.getPicture().isEmpty() 
                && !questionBank.getPicture().startsWith("/img/")) {
            // 如果没有上传文件但提供了图片URL，尝试下载图片（保持原逻辑）
            try {
                String newImagePath = FileUtils.saveQuestionBankImage(
                        questionBank.getPicture(), oldQuestionBank.getTitle());
                questionBank.setPicture(newImagePath);
                log.info("question bank image updated: {}", newImagePath);
            } catch (IOException e) {
                log.error("question bank image update failed", e);
            }
        }
        
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion
}
