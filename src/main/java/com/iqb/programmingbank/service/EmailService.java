package com.iqb.programmingbank.service;

/**
 * 邮件服务
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     * @param to 收件人
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String to, String code);
} 