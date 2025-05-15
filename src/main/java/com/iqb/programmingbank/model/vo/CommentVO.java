package com.iqb.programmingbank.model.vo;

import com.iqb.programmingbank.model.entity.Comment;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 评论视图
 */
@Data
public class CommentVO implements Serializable {

    /**
     * id
     */
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
     * 评论用户信息
     */
    private UserVO user;

    /**
     * 回复的评论
     */
    private List<CommentVO> children;

    /**
     * 被回复的用户信息（如果是回复某条评论）
     */
    private UserVO replyUser;

    /**
     * 对象转封装类
     *
     * @param comment
     * @return
     */
    public static CommentVO objToVo(Comment comment) {
        if (comment == null) {
            return null;
        }
        CommentVO commentVO = new CommentVO();
        BeanUtils.copyProperties(comment, commentVO);
        return commentVO;
    }
} 