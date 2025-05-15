package com.iqb.programmingbank.model.dto.question;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.iqb.programmingbank.config.TagsDeserializer;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 编辑题目请求
 *
 * @author zeden
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Data
public class QuestionEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表
     */
    @JsonDeserialize(using = TagsDeserializer.class)
    private List<String> tags;

    /**
     * 推荐答案
     */
    private String answer;

    private static final long serialVersionUID = 1L;
}