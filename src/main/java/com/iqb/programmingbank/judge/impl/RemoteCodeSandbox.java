package com.iqb.programmingbank.judge.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.iqb.programmingbank.judge.CodeSandbox;
import com.iqb.programmingbank.judge.model.ExecuteCodeRequest;
import com.iqb.programmingbank.judge.model.ExecuteCodeResponse;
import org.apache.commons.lang3.StringUtils;

//远程代码沙箱（实际调用接口的沙箱）
public class RemoteCodeSandbox implements CodeSandbox {


    //传递的请求头，安全性校验
    private static final String AUTH_REQUEST_HEADER = "auth";
    //传递的密钥
    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        System.out.println("远程代码沙箱");

        //调用我们自己实现的代码沙箱的接口，这里的接口目前使用的是java原生方式，docker也做了但是需要在linux部署才可以进行调用，所以暂时先用java原生方式
        String url = "http://localhost:8090/executeCode";
        //hutool工具包的方法
        String json = JSONUtil.toJsonStr(executeCodeRequest);
        String responseStr = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER,AUTH_REQUEST_SECRET) //发请求的时候带上请求头和密钥，要和沙箱服务一致，这样才能调用成功 保证安全性
                .body(json)
                .execute()
                .body(); //直接调用body方法，获取返回结果中的body信息

        if(StringUtils.isBlank(responseStr)){
            return null;
        }
        return JSONUtil.toBean(responseStr,ExecuteCodeResponse.class);
    }
}
