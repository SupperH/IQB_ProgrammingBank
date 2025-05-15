package com.iqb.programmingbank.model.vo;

import com.iqb.programmingbank.model.entity.Question;
import lombok.Data;

import java.util.List;

//学习记录返回前端数据用
@Data
public class PracticeRecordVO {
    //总学习条数
    private Integer count;
    //日期
    private String date;
    //题目列表
    private List<Question> questionList;

}
