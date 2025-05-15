package com.iqb.programmingbank.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户重置密码请求
 */
@Data
public class UserResetPasswordRequest implements Serializable {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String verifyCode;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
} 