package com.iqb.programmingbank.controller;

import com.iqb.programmingbank.common.BaseResponse;
import com.iqb.programmingbank.common.ErrorCode;
import com.iqb.programmingbank.common.ResultUtils;
import com.iqb.programmingbank.exception.BusinessException;
import com.iqb.programmingbank.exception.ThrowUtils;
import com.iqb.programmingbank.model.dto.comment.CommentAddRequest;
import com.iqb.programmingbank.model.entity.Comment;
import com.iqb.programmingbank.model.entity.User;
import com.iqb.programmingbank.model.vo.CommentVO;
import com.iqb.programmingbank.service.CommentService;
import com.iqb.programmingbank.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 评论接口
 */
@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController {

    @Resource
    private CommentService commentService;

    @Resource
    private UserService userService;

    /**
     * 添加评论
     *
     * @param commentAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Integer> addComment(@RequestBody CommentAddRequest commentAddRequest, HttpServletRequest request) {
        if (commentAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        log.info("收到添加评论请求：{}", commentAddRequest);
        
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        
        // 转换为Comment对象
        Comment comment = new Comment();
        BeanUtils.copyProperties(commentAddRequest, comment);
        
        // 校验
        commentService.validComment(comment, true);
        
        // 设置评论用户ID和时间
        comment.setUserId(loginUser.getId());
        comment.setCreateTime(new Date());
        comment.setUpdateTime(new Date());
        
        // 设置默认值
        if (comment.getBid() == null) {
            comment.setBid(0);
        }
        if (comment.getRid() == null) {
            comment.setRid(0);
        }
        comment.setIsDelete(0);
        
        // 保存评论
        boolean result = commentService.save(comment);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        log.info("评论添加成功，ID={}", comment.getId());
        
        return ResultUtils.success(comment.getId());
    }

    /**
     * 根据题目ID获取评论列表
     *
     * @param questionId
     * @param request
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<CommentVO>> listCommentByQuestionId(Integer questionId, HttpServletRequest request) {
        // 参数有效性判断，questionId必须大于0
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        log.info("get question comment list, questionId={}", questionId);
        
        try {
            // 调用服务获取评论列表
            List<CommentVO> commentVOList = commentService.listCommentByQuestionId(questionId, request);
            
            // 打印返回的评论列表第一条数据(如果有)作为调试信息
            if (commentVOList != null && !commentVOList.isEmpty()) {
                CommentVO firstComment = commentVOList.get(0);
                log.info("first comment data: id={}, content={}, user={}，userName={}, userAvatar={}", 
                    firstComment.getId(), 
                    firstComment.getContent(),
                    firstComment.getUser(),
                    firstComment.getUser() != null ? firstComment.getUser().getUserName() : null,
                    firstComment.getUser() != null ? firstComment.getUser().getUserAvatar() : null);
            }
            
            // 即使没有评论也返回成功，只是数据为空列表
            log.info("get question comment success, commentCount={}", commentVOList.size());
            return ResultUtils.success(commentVOList);
        } catch (Exception e) {
            // 记录异常日志
            log.error("get question comment list failed", e);
            // 发生异常时返回空列表，确保前端显示不会出错
            return ResultUtils.success(new ArrayList<>());
        }
    }

    /**
     * 删除评论（仅评论作者和管理员可删除）
     *
     * @param id
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestParam("id") Integer id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        log.info("delete comment request, id={}", id);
        
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        
        // 判断是否存在
        Comment comment = commentService.getById(id);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 仅评论作者和管理员可删除
        if (!comment.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        
        // 删除评论
        boolean result = commentService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        log.info("comment delete success, id={}", id);
        
        return ResultUtils.success(true);
    }
}