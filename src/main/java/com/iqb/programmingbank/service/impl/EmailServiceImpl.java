package com.iqb.programmingbank.service.impl;

import com.iqb.programmingbank.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public boolean sendVerificationCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("IQB question bank - Vertify Code");
            message.setText("Your vertify code is：" + code + "，avaleble in 5 minutes。\n\nIgnore this email if not done by me。");
            mailSender.send(message);
            log.info("Vertify code email sent successfully，to：{}", to);
            return true;
        } catch (Exception e) {
            log.error("Vertify code email sent failed，to：{}，erro message：{}", to, e.getMessage());
            return false;
        }
    }
} 