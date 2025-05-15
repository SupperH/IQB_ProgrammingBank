package com.iqb.programmingbank.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 学习路线图请求
 */
@Data
public class LearningPathRequest implements Serializable {

    /**
     * 开发者等级
     */
    private String grade;

    /**
     * 工作经验
     */
    private String experience;

    /**
     * 专业领域
     */
    private String expertise;

    private static final long serialVersionUID = 1L;
} 