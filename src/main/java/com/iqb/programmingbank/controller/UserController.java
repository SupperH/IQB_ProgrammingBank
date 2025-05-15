package com.iqb.programmingbank.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iqb.programmingbank.common.BaseResponse;
import com.iqb.programmingbank.common.DeleteRequest;
import com.iqb.programmingbank.common.ErrorCode;
import com.iqb.programmingbank.common.ResultUtils;
import com.iqb.programmingbank.config.WxOpenConfig;
import com.iqb.programmingbank.constant.UserConstant;
import com.iqb.programmingbank.exception.BusinessException;
import com.iqb.programmingbank.exception.ThrowUtils;
import com.iqb.programmingbank.model.dto.user.*;
import com.iqb.programmingbank.model.dto.user.*;
import com.iqb.programmingbank.model.entity.QuestionBank;
import com.iqb.programmingbank.model.entity.User;
import com.iqb.programmingbank.model.vo.LoginUserVO;
import com.iqb.programmingbank.model.vo.PracticeRecordVO;
import com.iqb.programmingbank.model.vo.UserVO;
import com.iqb.programmingbank.service.EmailService;
import com.iqb.programmingbank.service.QuestionBankService;
import com.iqb.programmingbank.service.UserService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.mp.api.WxMpService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.iqb.programmingbank.service.impl.UserServiceImpl.SALT;
import com.iqb.programmingbank.utils.FileUtils;

/**
 * 用户接口
 *
@author zeden
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private WxOpenConfig wxOpenConfig;

    @Resource
    private EmailService emailService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private QuestionBankService questionBankService;

    private static final String VERIFY_CODE_PREFIX = "verify_code:";
    private static final long VERIFY_CODE_EXPIRE = 5; // 验证码有效期（分钟）

    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String email = userRegisterRequest.getEmail();
        String verifyCode = userRegisterRequest.getVerifyCode();
        
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, email, verifyCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        // vertify code
        String key = VERIFY_CODE_PREFIX + email;
        String savedVerifyCode = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(savedVerifyCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "verify code expired");
        }
        if (!verifyCode.equals(savedVerifyCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "verify code incorrect");
        }
        
        // register
        long result = userService.userRegister(userAccount, userPassword, checkPassword,email);
        
        // registed delete code
        stringRedisTemplate.delete(key);
        
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户登录（微信开放平台）
     */
    @GetMapping("/login/wx_open")
    public BaseResponse<LoginUserVO> userLoginByWxOpen(HttpServletRequest request, HttpServletResponse response,
                                                       @RequestParam("code") String code) {
        WxOAuth2AccessToken accessToken;
        try {
            WxMpService wxService = wxOpenConfig.getWxMpService();
            accessToken = wxService.getOAuth2Service().getAccessToken(code);
            WxOAuth2UserInfo userInfo = wxService.getOAuth2Service().getUserInfo(accessToken, code);
            String unionId = userInfo.getUnionId();
            String mpOpenId = userInfo.getOpenid();
            if (StringUtils.isAnyBlank(unionId, mpOpenId)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "login failed, system error");
            }
            return ResultUtils.success(userService.userLoginByMpOpen(userInfo, request));
        } catch (Exception e) {
            log.error("userLoginByWxOpen error", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "login failed, system error");
        }
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * @param userAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + defaultPassword).getBytes());
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                            HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑用户信息（支持用户和管理员）
     *
     * @param userEditRequest 编辑请求（包含需要更新的字段）
     * @param request HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editUser(@RequestBody UserEditRequest userEditRequest, HttpServletRequest request) {
        if (userEditRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 构建更新对象
        User user = new User();
        BeanUtils.copyProperties(userEditRequest, user);
        
        // 如果密码不为空，进行加密处理
        String userPassword = userEditRequest.getUserPassword();
        if (StringUtils.isNotBlank(userPassword)) {
            // 使用与注册相同的加密逻辑
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            user.setUserPassword(encryptPassword);
        }
        
        // 如果是用户编辑自己，强制设置 ID 为当前用户 ID（防止越权修改）
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        BaseResponse<User> response = getUserById(id, request);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                   HttpServletRequest request) {
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        return ResultUtils.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                       HttpServletRequest request) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    /**
     * 更新个人信息
     *
     * @param userUpdateMyRequest
     * @param request
     * @return
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        BeanUtils.copyProperties(userUpdateMyRequest, user);
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 添加用户签到记录
     *
     * @param request
     * @return 当前是否已签到成功
     */
    @PostMapping("/add/sign_in")
    public BaseResponse<Boolean> addUserSignIn(HttpServletRequest request , @RequestParam String questionTitle) {
        // 必须要登录才能签到
        User loginUser = userService.getLoginUser(request);
        boolean result = userService.addUserSignIn(loginUser.getId(),questionTitle);
        return ResultUtils.success(result);
    }

    /**
     * 添加用户签到记录
     *
     * @param request
     * @return 当前是否已签到成功
     */
    @PostMapping("/get/sign_in_info")
    public BaseResponse<PracticeRecordVO> getUserSignInInfo(HttpServletRequest request , @RequestParam String selectedDate) {
        // 必须要登录才能获取
        User loginUser = userService.getLoginUser(request);
        PracticeRecordVO result = userService.getUserSignInInfo(loginUser.getId(),selectedDate);
        String jsonStr = JSONUtil.toJsonStr(result);
        System.out.println(jsonStr);
        return ResultUtils.success(result);
    }

    /**
     * 获取用户签到记录
     *
     * @param year    年份（为空表示当前年份）
     * @param request
     * @return 签到记录映射
     */
    @GetMapping("/get/sign_in")
    public BaseResponse<List<Integer>> getUserSignInRecord(Integer year, HttpServletRequest request) {
        // 必须要登录才能获取
        User loginUser = userService.getLoginUser(request);
        List<Integer> userSignInRecord = userService.getUserSignInRecord(loginUser.getId(), year);
        return ResultUtils.success(userSignInRecord);
    }

    /**
     * 发送注册验证码
     * @param email 邮箱
     * @return
     */
    @PostMapping("/sendVerifyCode")
    public BaseResponse<Boolean> sendVerifyCode(@RequestParam String email) {
        if (StringUtils.isBlank(email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "email cannot be empty");
        }
        
        // 校验邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "email format error");
        }
        
        // 查询邮箱是否已被注册
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        long count = userService.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "email already registered");
        }
        
        // 生成6位随机验证码
        String verifyCode = String.format("%06d", new Random().nextInt(1000000));
        
        // 发送验证码
        boolean success = emailService.sendVerificationCode(email, verifyCode);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR, "verify code send failed");
        
        // 将验证码保存到Redis
        String key = VERIFY_CODE_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, verifyCode, VERIFY_CODE_EXPIRE, TimeUnit.MINUTES);
        
        return ResultUtils.success(true);
    }

    /**
     * 上传用户头像
     *
     * @param file    头像文件
     * @param request HTTP请求
     * @return 更新后的头像URL
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file, 
                                           HttpServletRequest request) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "upload file is empty");
        }
        
        // 验证文件
        if (!FileUtils.isImage(file)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "file type error");
        }
        
        if (FileUtils.exceedsMaxSize(file)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "file size cannot exceed 5MB");
        }
        
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        
        // 获取文件后缀
        String fileName = file.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        
        try {
            // 生成新的文件名
            String newFileName = UUID.randomUUID().toString() + suffix;
            
            // 1. 保存到后端项目目录（保持原来的逻辑）
            String backendDir = System.getProperty("user.dir") + File.separator + "img" 
                             + File.separator + userId;
            Path backendPath = Paths.get(backendDir);
            if (!Files.exists(backendPath)) {
                Files.createDirectories(backendPath);
            }
            Path backendFilePath = backendPath.resolve(newFileName);
            Files.copy(file.getInputStream(), backendFilePath);
            log.info("avatar saved to backend directory: {}", backendFilePath);
            
            // 2. 同时保存到前端项目的静态资源目录（如果存在）
            String frontendStaticDir = FileUtils.findFrontendStaticDir();
            if (frontendStaticDir != null) {
                String frontendImgDir = frontendStaticDir + File.separator + "img" 
                                     + File.separator + userId;
                Path frontendPath = Paths.get(frontendImgDir);
                if (!Files.exists(frontendPath)) {
                    Files.createDirectories(frontendPath);
                }
                Path frontendFilePath = frontendPath.resolve(newFileName);
                // 重新获取输入流，因为上面的copy操作已经消耗了输入流
                Files.copy(file.getInputStream(), frontendFilePath);
                log.info("avatar saved to frontend static resource directory: {}", frontendFilePath);
            } else {
                log.warn("frontend static resource directory not found, avatar saved to backend");
            }
            
            // 生成可访问的URL
            String avatarUrl = "/img/" + userId + "/" + newFileName;
            
            // 更新数据库中的头像字段
            User user = new User();
            user.setId(userId);
            user.setUserAvatar(avatarUrl);
            boolean result = userService.updateById(user);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "avatar update failed");
            
            return ResultUtils.success(avatarUrl);
        } catch (IOException e) {
            log.error("file upload failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "file upload failed: " + e.getMessage());
        }
    }

    /**
     * 发送忘记密码验证码
     * @param userAccount 用户账号
     * @param email 邮箱
     * @return 是否发送成功
     */
    @PostMapping("/sendResetPasswordCode")
    public BaseResponse<Boolean> sendResetPasswordCode(
            @RequestParam String userAccount, 
            @RequestParam String email) {
        if (StringUtils.isAnyBlank(userAccount, email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "account or email cannot be empty");
        }
        
        // 校验邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "email format error");
        }
        
        // 查询用户是否存在且邮箱匹配
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("email", email);
        User user = userService.getOne(queryWrapper);
        
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "account and email do not match or user does not exist");
        }
        
        // 生成6位随机验证码
        String verifyCode = String.format("%06d", new Random().nextInt(1000000));
        
        // 发送验证码
        boolean success = emailService.sendVerificationCode(email, verifyCode);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR, "verify code send failed");
        
        // 将验证码保存到Redis
        String key = "reset_pwd_code:" + userAccount + ":" + email;
        stringRedisTemplate.opsForValue().set(key, verifyCode, VERIFY_CODE_EXPIRE, TimeUnit.MINUTES);
        
        return ResultUtils.success(true);
    }
    
    /**
     * 重置密码
     * @param userResetPasswordRequest 重置密码请求
     * @return 是否重置成功
     */
    @PostMapping("/resetPassword")
    public BaseResponse<Boolean> resetPassword(@RequestBody UserResetPasswordRequest userResetPasswordRequest) {
        if (userResetPasswordRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        String userAccount = userResetPasswordRequest.getUserAccount();
        String email = userResetPasswordRequest.getEmail();
        String verifyCode = userResetPasswordRequest.getVerifyCode();
        String newPassword = userResetPasswordRequest.getNewPassword();
        String checkPassword = userResetPasswordRequest.getCheckPassword();
        
        // 参数校验
        if (StringUtils.isAnyBlank(userAccount, email, verifyCode, newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "parameters cannot be empty");
        }
        
        // 校验新密码
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "password length cannot be less than 8");
        }
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "two input passwords are inconsistent");
        }
        
        // 校验验证码
        String key = "reset_pwd_code:" + userAccount + ":" + email;
        String savedVerifyCode = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(savedVerifyCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "verify code expired");
        }
        if (!verifyCode.equals(savedVerifyCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "verify code incorrect");
        }
        
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("email", email);
        User user = userService.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "user does not exist");
        }
        
        // 加密新密码
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + newPassword).getBytes());
        
        // 更新用户密码
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserPassword(encryptPassword);
        boolean result = userService.updateById(updateUser);
        
        // 操作成功后删除验证码
        if (result) {
            stringRedisTemplate.delete(key);
        }
        
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "password reset failed");
        return ResultUtils.success(true);
    }

    /**
     * 推荐学习路线图
     *
     * @param learningPathRequest 学习路线图请求
     * @return 推荐的学习路线图
     */
    @PostMapping("/recommendLearningPath")
    public BaseResponse<LearningPathResponse> recommendLearningPath(@RequestBody LearningPathRequest learningPathRequest) {
        if (learningPathRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        String grade = learningPathRequest.getGrade();
        String experience = learningPathRequest.getExperience();
        String expertise = learningPathRequest.getExpertise();
        
        if (StringUtils.isAnyBlank(grade, experience, expertise)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        
        // 根据用户选择的参数生成推荐路线
        List<LearningPathResponse.Module> modules = generateLearningPath(grade, experience, expertise);
        
        LearningPathResponse response = new LearningPathResponse();
        response.setData(modules);
        
        return ResultUtils.success(response);
    }

    /**
     * 生成学习路线
     *
     * @param grade 开发者等级
     * @param experience 工作经验
     * @param expertise 专业领域
     * @return 推荐模块列表
     */
    private List<LearningPathResponse.Module> generateLearningPath(String grade, String experience, String expertise) {
        List<LearningPathResponse.Module> modules = new ArrayList<>();
        
        // 解析工作经验年限
        int yearsOfExperience = parseExperience(experience);
        
        // 根据专业领域选择基础题库
        switch (expertise.toLowerCase()) {
            case "java":
                addJavaModules(modules, grade, yearsOfExperience);
                break;
            case "python":
                addPythonModules(modules, grade, yearsOfExperience);
                break;
            case "javascript":
                addJavaScriptModules(modules, grade, yearsOfExperience);
                break;
            case "frontend":
                addFrontendModules(modules, grade, yearsOfExperience);
                break;
            case "backend":
                addBackendModules(modules, grade, yearsOfExperience);
                break;
            case "fullstack":
                addFullstackModules(modules, grade, yearsOfExperience);
                break;
            default:
                addGeneralModules(modules, grade, yearsOfExperience);
        }
        
        return modules;
    }

    /**
     * 解析工作经验年限
     */
    private int parseExperience(String experience) {
        if (experience == null) {
            return 0;
        }
        // 处理 "5+ years" 的情况
        if (experience.contains("+")) {
            return 5;
        }
        // 提取数字
        String years = experience.replaceAll("[^0-9]", "");
        return years.isEmpty() ? 0 : Integer.parseInt(years);
    }

    /**
     * 添加Java相关模块
     */
    private void addJavaModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询Java相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "Java")
                .or()
                .like("description", "Java");
        List<QuestionBank> javaBanks = questionBankService.list(queryWrapper);
        
        // 添加基础Java题库
        for (QuestionBank bank : javaBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("Java " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "Java基础", "Java编程基础", "Java基础题库");
            addModule(modules, "面向对象", "Java面向对象编程", "OOP题库");
            addModule(modules, "MySQL基础", "数据库基础操作", "MySQL基础题库");
            addModule(modules, "Spring基础", "Spring框架入门", "Spring基础题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "Java进阶", "Java高级特性", "Java进阶题库");
            addModule(modules, "Spring进阶", "Spring框架深入", "Spring进阶题库");
            addModule(modules, "MySQL进阶", "数据库优化", "MySQL进阶题库");
            addModule(modules, "Redis基础", "Redis缓存入门", "Redis基础题库");
            addModule(modules, "设计模式", "常用设计模式", "设计模式题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "Java架构", "Java系统架构设计", "架构设计题库");
            addModule(modules, "性能优化", "Java性能调优", "性能优化题库");
            addModule(modules, "分布式系统", "分布式架构设计", "分布式题库");
            addModule(modules, "微服务", "微服务架构", "微服务题库");
            addModule(modules, "高并发", "高并发编程", "高并发题库");
        }
    }

    /**
     * 添加Python相关模块
     */
    private void addPythonModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询Python相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "Python")
                .or()
                .like("description", "Python");
        List<QuestionBank> pythonBanks = questionBankService.list(queryWrapper);
        
        // 添加基础Python题库
        for (QuestionBank bank : pythonBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("Python " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "Python基础", "Python编程基础", "Python基础题库");
            addModule(modules, "数据结构", "Python数据结构", "数据结构题库");
            addModule(modules, "Django基础", "Web开发入门", "Django基础题库");
            addModule(modules, "SQL基础", "数据库基础", "SQL基础题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "Python进阶", "Python高级特性", "Python进阶题库");
            addModule(modules, "Django进阶", "Web开发进阶", "Django进阶题库");
            addModule(modules, "Flask框架", "轻量级Web框架", "Flask题库");
            addModule(modules, "数据分析", "Python数据分析", "数据分析题库");
            addModule(modules, "爬虫技术", "网络爬虫开发", "爬虫题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "Python架构", "Python系统架构", "架构设计题库");
            addModule(modules, "性能优化", "Python性能调优", "性能优化题库");
            addModule(modules, "机器学习", "机器学习基础", "机器学习题库");
            addModule(modules, "分布式系统", "分布式架构设计", "分布式题库");
            addModule(modules, "微服务", "微服务架构", "微服务题库");
        }
    }

    /**
     * 添加JavaScript相关模块
     */
    private void addJavaScriptModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询JavaScript相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "JavaScript")
                .or()
                .like("description", "JavaScript");
        List<QuestionBank> jsBanks = questionBankService.list(queryWrapper);
        
        // 添加基础JavaScript题库
        for (QuestionBank bank : jsBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("JavaScript " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "JavaScript基础", "JavaScript编程基础", "JS基础题库");
            addModule(modules, "DOM操作", "DOM操作与事件", "DOM题库");
            addModule(modules, "ES6基础", "ES6新特性", "ES6基础题库");
            addModule(modules, "jQuery", "jQuery基础", "jQuery题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "JavaScript进阶", "JavaScript高级特性", "JS进阶题库");
            addModule(modules, "Vue.js", "Vue.js框架", "Vue题库");
            addModule(modules, "React基础", "React入门", "React基础题库");
            addModule(modules, "Node.js", "Node.js开发", "Node.js题库");
            addModule(modules, "TypeScript", "TypeScript基础", "TypeScript题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "前端架构", "前端系统架构", "架构设计题库");
            addModule(modules, "性能优化", "前端性能优化", "性能优化题库");
            addModule(modules, "React进阶", "React高级特性", "React进阶题库");
            addModule(modules, "微前端", "微前端架构", "微前端题库");
            addModule(modules, "工程化", "前端工程化", "工程化题库");
        }
    }

    /**
     * 添加前端相关模块
     */
    private void addFrontendModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询前端相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "前端")
                .or()
                .like("description", "前端");
        List<QuestionBank> frontendBanks = questionBankService.list(queryWrapper);
        
        // 添加基础前端题库
        for (QuestionBank bank : frontendBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("前端 " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "HTML/CSS", "网页基础", "HTML/CSS题库");
            addModule(modules, "JavaScript基础", "JavaScript编程基础", "JS基础题库");
            addModule(modules, "响应式设计", "移动端适配", "响应式题库");
            addModule(modules, "浏览器基础", "浏览器工作原理", "浏览器题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "前端框架", "主流前端框架", "框架题库");
            addModule(modules, "工程化", "前端工程化", "工程化题库");
            addModule(modules, "性能优化", "前端性能优化", "性能优化题库");
            addModule(modules, "TypeScript", "TypeScript基础", "TypeScript题库");
            addModule(modules, "状态管理", "前端状态管理", "状态管理题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "前端架构", "前端系统架构", "架构设计题库");
            addModule(modules, "微前端", "微前端架构", "微前端题库");
            addModule(modules, "跨端开发", "跨平台开发", "跨端题库");
            addModule(modules, "低代码", "低代码平台", "低代码题库");
            addModule(modules, "可视化", "数据可视化", "可视化题库");
        }
    }

    /**
     * 添加后端相关模块
     */
    private void addBackendModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询后端相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "后端")
                .or()
                .like("description", "后端");
        List<QuestionBank> backendBanks = questionBankService.list(queryWrapper);
        
        // 添加基础后端题库
        for (QuestionBank bank : backendBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("后端 " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "数据库基础", "数据库基础操作", "数据库题库");
            addModule(modules, "API设计", "RESTful API设计", "API设计题库");
            addModule(modules, "缓存基础", "Redis基础", "Redis基础题库");
            addModule(modules, "服务器基础", "Linux基础", "Linux题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "系统设计", "后端系统设计", "系统设计题库");
            addModule(modules, "性能优化", "后端性能优化", "性能优化题库");
            addModule(modules, "消息队列", "消息队列应用", "消息队列题库");
            addModule(modules, "分布式基础", "分布式系统基础", "分布式基础题库");
            addModule(modules, "安全基础", "Web安全基础", "安全题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "架构设计", "系统架构设计", "架构设计题库");
            addModule(modules, "分布式系统", "分布式系统设计", "分布式题库");
            addModule(modules, "微服务", "微服务架构", "微服务题库");
            addModule(modules, "高并发", "高并发编程", "高并发题库");
            addModule(modules, "云原生", "云原生架构", "云原生题库");
        }
    }

    /**
     * 添加全栈相关模块
     */
    private void addFullstackModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询全栈相关的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "全栈")
                .or()
                .like("description", "全栈");
        List<QuestionBank> fullstackBanks = questionBankService.list(queryWrapper);
        
        // 添加基础全栈题库
        for (QuestionBank bank : fullstackBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("全栈 " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "全栈基础", "全栈开发基础", "全栈基础题库");
            addModule(modules, "前后端交互", "前后端数据交互", "交互题库");
            addModule(modules, "数据库基础", "数据库基础操作", "数据库题库");
            addModule(modules, "Web基础", "Web开发基础", "Web基础题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "全栈进阶", "全栈开发进阶", "全栈进阶题库");
            addModule(modules, "项目实战", "全栈项目实战", "项目实战题库");
            addModule(modules, "性能优化", "全栈性能优化", "性能优化题库");
            addModule(modules, "工程化", "全栈工程化", "工程化题库");
            addModule(modules, "DevOps基础", "DevOps入门", "DevOps题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "架构设计", "全栈架构设计", "架构设计题库");
            addModule(modules, "微服务", "微服务架构", "微服务题库");
            addModule(modules, "云原生", "云原生架构", "云原生题库");
            addModule(modules, "技术管理", "技术团队管理", "管理题库");
            addModule(modules, "创新技术", "新技术应用", "创新题库");
        }
    }

    /**
     * 添加通用模块
     */
    private void addGeneralModules(List<LearningPathResponse.Module> modules, String grade, int yearsOfExperience) {
        // 查询通用题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", "通用")
                .or()
                .like("description", "通用");
        List<QuestionBank> generalBanks = questionBankService.list(queryWrapper);
        
        // 添加基础通用题库
        for (QuestionBank bank : generalBanks) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule("通用 " + bank.getTitle());
            module.setDescription(bank.getDescription());
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
        
        // 根据工作年限和等级添加特定模块
        if (yearsOfExperience <= 1) {
            // 初级开发者（1年及以下）
            addModule(modules, "编程基础", "计算机基础知识", "基础题库");
            addModule(modules, "数据结构", "基础数据结构", "数据结构题库");
            addModule(modules, "算法基础", "基础算法", "算法题库");
            addModule(modules, "Git基础", "版本控制基础", "Git题库");
        } else if (yearsOfExperience <= 3) {
            // 中级开发者（1-3年）
            addModule(modules, "设计模式", "软件设计模式", "设计模式题库");
            addModule(modules, "系统设计", "基础系统设计", "系统设计题库");
            addModule(modules, "测试基础", "软件测试基础", "测试题库");
            addModule(modules, "CI/CD", "持续集成部署", "CI/CD题库");
            addModule(modules, "代码质量", "代码质量提升", "代码质量题库");
        } else {
            // 高级开发者（3年以上）
            addModule(modules, "架构设计", "系统架构设计", "架构设计题库");
            addModule(modules, "性能优化", "系统性能优化", "性能优化题库");
            addModule(modules, "技术管理", "技术团队管理", "管理题库");
            addModule(modules, "项目管理", "项目管理方法", "项目管理题库");
            addModule(modules, "创新技术", "新技术应用", "创新题库");
        }
    }

    /**
     * 添加模块
     */
    private void addModule(List<LearningPathResponse.Module> modules, String moduleName, String description, String bankName) {
        // 查询对应的题库
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("title", bankName);
        QuestionBank bank = questionBankService.getOne(queryWrapper);
        
        if (bank != null) {
            LearningPathResponse.Module module = new LearningPathResponse.Module();
            module.setModule(moduleName);
            module.setDescription(description);
            module.setQuestionBankId(bank.getId());
            module.setQuestionBankName(bank.getTitle());
            modules.add(module);
        }
    }

}
