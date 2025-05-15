package com.iqb.programmingbank.controller;

import com.iqb.programmingbank.common.BaseResponse;
import com.iqb.programmingbank.common.ErrorCode;
import com.iqb.programmingbank.common.ResultUtils;
import com.iqb.programmingbank.exception.ThrowUtils;
import com.iqb.programmingbank.judge.impl.RemoteCodeSandbox;
import com.iqb.programmingbank.judge.model.ExecuteCodeRequest;
import com.iqb.programmingbank.judge.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 判题接口 调用8090的OJ服务
 */
@RestController
@RequestMapping("/oj")
public class OJController {


    @PostMapping("/judge")
    public BaseResponse<ExecuteCodeResponse> ojJudge(@RequestBody ExecuteCodeRequest executeCodeRequest){
        ThrowUtils.throwIf(executeCodeRequest.getCode()==null, ErrorCode.PARAMS_ERROR);

        RemoteCodeSandbox remoteCodeSandbox = new RemoteCodeSandbox();
        ExecuteCodeResponse executeCodeResponse = remoteCodeSandbox.executeCode(executeCodeRequest);

        return ResultUtils.success(executeCodeResponse);

    }

}
