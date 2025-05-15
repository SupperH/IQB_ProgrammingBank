package com.iqb.programmingbank.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习路线图响应
 */
@Data
public class LearningPathResponse implements Serializable {

    /**
     * 推荐模块列表
     */
    private List<Module> data;

    @Data
    public static class Module implements Serializable {
        /**
         * 模块名称
         */
        private String module;

        /**
         * 模块描述
         */
        private String description;

        /**
         * 题库ID
         */
        private Long questionBankId;

        /**
         * 题库名称
         */
        private String questionBankName;
    }

    private static final long serialVersionUID = 1L;
} 