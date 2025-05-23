package com.iqb.programmingbank.judge.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {

    //程序执行信息
    private String message;

    //消耗内存
    private Long memory;

    //消耗时间kb
    private Long time;
}
