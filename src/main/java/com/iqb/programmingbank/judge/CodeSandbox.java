package com.iqb.programmingbank.judge;

import com.iqb.programmingbank.judge.model.ExecuteCodeRequest;
import com.iqb.programmingbank.judge.model.ExecuteCodeResponse;

public interface CodeSandbox {

    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
