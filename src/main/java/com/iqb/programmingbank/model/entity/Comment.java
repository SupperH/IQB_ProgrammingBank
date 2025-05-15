package com.iqb.programmingbank.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 评论
 * @TableName comment
 */
@TableName(value = "comment")
@Data
public class Comment implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

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
     * 评论用户的id
     */
    private Long userId;

    /**
     * 内容
     */
    private String content;

    /**
     * 创建日期
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
} 