package com.iqb.programmingbank.model.dto.comment;

import lombok.Data;

import java.io.Serializable;

/**
 * 查询评论请求
 */
@Data
public class CommentQueryRequest implements Serializable {

    /**
     * 题目id
     */
    private Integer questionId;

    private static final long serialVersionUID = 1L;
} 