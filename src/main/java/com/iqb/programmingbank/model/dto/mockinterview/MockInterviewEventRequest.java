package com.iqb.programmingbank.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试事件请求
 *
 * @author zeden
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Data
public class MockInterviewEventRequest implements Serializable {

    /**
     * 事件类型
     */
    private String event;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 房间 ID
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}