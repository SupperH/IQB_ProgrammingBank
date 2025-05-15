package com.iqb.programmingbank.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iqb.programmingbank.model.entity.Comment;
import com.iqb.programmingbank.model.vo.CommentVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 评论服务
 */
public interface CommentService extends IService<Comment> {

    /**
     * 校验评论信息
     *
     * @param comment
     * @param add
     */
    void validComment(Comment comment, boolean add);

    /**
     * 获取评论封装
     *
     * @param comment
     * @param request
     * @return
     */
    CommentVO getCommentVO(Comment comment, HttpServletRequest request);

    /**
     * 根据题目id获取评论列表
     *
     * @param questionId
     * @param request
     * @return
     */
    List<CommentVO> listCommentByQuestionId(Integer questionId, HttpServletRequest request);
} 