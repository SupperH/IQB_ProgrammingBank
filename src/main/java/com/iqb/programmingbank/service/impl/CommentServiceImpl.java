package com.iqb.programmingbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iqb.programmingbank.exception.BusinessException;
import com.iqb.programmingbank.common.ErrorCode;
import com.iqb.programmingbank.mapper.CommentMapper;
import com.iqb.programmingbank.model.entity.Comment;
import com.iqb.programmingbank.model.entity.User;
import com.iqb.programmingbank.model.vo.CommentVO;
import com.iqb.programmingbank.model.vo.UserVO;
import com.iqb.programmingbank.service.CommentService;
import com.iqb.programmingbank.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务实现
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    private UserService userService;

    /**
     * 校验评论信息
     *
     * @param comment
     * @param add
     */
    @Override
    public void validComment(Comment comment, boolean add) {
        if (comment == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        // 创建时必须要有内容和题目ID
        if (add) {
            if (comment.getQuestionId() == null || comment.getQuestionId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题目不存在");
            }
            if (StringUtils.isBlank(comment.getContent())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能为空");
            }
        }
        
        // 有参数则校验
        if (StringUtils.isNotBlank(comment.getContent()) && comment.getContent().length() > 1000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容过长");
        }
    }

    /**
     * 获取评论封装
     *
     * @param comment
     * @param request
     * @return
     */
    @Override
    public CommentVO getCommentVO(Comment comment, HttpServletRequest request) {
        if (comment == null) {
            return null;
        }
        
        CommentVO commentVO = CommentVO.objToVo(comment);
        
        // 1. 关联查询用户信息
        Long userId = comment.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            if (user != null) {
                UserVO userVO = new UserVO();
                userVO.setId(user.getId());
                userVO.setUserName(user.getUserName());
                userVO.setUserAvatar(user.getUserAvatar());
                userVO.setUserRole(user.getUserRole());
                commentVO.setUser(userVO);
            }
        }
        
        // 2. 如果是回复评论，查询被回复的用户信息
        Integer bid = comment.getBid();
        if (bid != null && bid > 0) {
            Comment replyComment = this.getById(bid);
            if (replyComment != null) {
                Long replyUserId = replyComment.getUserId();
                if (replyUserId != null && replyUserId > 0) {
                    User replyUser = userService.getById(replyUserId);
                    if (replyUser != null) {
                        UserVO replyUserVO = new UserVO();
                        replyUserVO.setId(replyUser.getId());
                        replyUserVO.setUserName(replyUser.getUserName());
                        replyUserVO.setUserAvatar(replyUser.getUserAvatar());
                        replyUserVO.setUserRole(replyUser.getUserRole());
                        commentVO.setReplyUser(replyUserVO);
                    }
                }
            }
        }
        
        return commentVO;
    }

    /**
     * 根据题目id获取评论列表（带有层级结构）
     *
     * @param questionId
     * @param request
     * @return
     */
    @Override
    public List<CommentVO> listCommentByQuestionId(Integer questionId, HttpServletRequest request) {
        // 参数校验
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询所有与该题目相关的评论
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        // 使用驼峰风格的字段名，与数据库表字段保持一致
        queryWrapper.eq("questionId", questionId).eq("isDelete", 0);
        queryWrapper.orderByDesc("createTime");
        List<Comment> commentList = this.list(queryWrapper);
        
        // 如果没有评论，直接返回空列表
        if (commentList == null || commentList.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 将评论列表转为VO列表
        List<CommentVO> commentVOList = commentList.stream()
                .map(comment -> getCommentVO(comment, request))
                .collect(Collectors.toList());
        
        // 构建评论树，将子评论放入父评论的children字段中
        // 1. 先找出所有顶级评论（rid = 0 或 null）
        List<CommentVO> topComments = commentVOList.stream()
                .filter(commentVO -> commentVO.getRid() == null || commentVO.getRid() == 0)
                .collect(Collectors.toList());
        
        // 2. 创建ID到评论的映射，方便查找
        Map<Integer, CommentVO> idToCommentMap = new HashMap<>();
        commentVOList.forEach(commentVO -> idToCommentMap.put(commentVO.getId(), commentVO));
        
        // 3. 将回复评论添加到对应父评论的children列表中
        commentVOList.stream()
                .filter(commentVO -> commentVO.getRid() != null && commentVO.getRid() > 0)
                .forEach(commentVO -> {
                    CommentVO parentComment = idToCommentMap.get(commentVO.getRid());
                    if (parentComment != null) {
                        if (parentComment.getChildren() == null) {
                            parentComment.setChildren(new ArrayList<>());
                        }
                        parentComment.getChildren().add(commentVO);
                    }
                });
        
        return topComments;
    }
} 