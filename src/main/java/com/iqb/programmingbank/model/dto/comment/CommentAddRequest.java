package com.iqb.programmingbank.model.dto.comment;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加评论请求
 */
@Data
public class CommentAddRequest implements Serializable {

    /**
     * 题目id
     */
    private Integer questionId;

    /**
     * 评论人的评论id，用于回复他人
     */
    private Integer bid;

    /**
     * 父id
     */
    private Integer rid;

    /**
     * 内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
} 