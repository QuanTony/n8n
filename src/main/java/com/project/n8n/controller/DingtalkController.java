package com.project.n8n.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Api(value = "dingTalk", description = "dingTalk")
@Controller
@RequestMapping("/dingTalk")
@Slf4j
public class DingtalkController {
    // 替换为你的钉钉AppSecret
    private static final String DING_APP_SECRET = "c-24LSVrYOVZOXjfDRHsuJ05Qp1a-EqGZ7nVzr8c4gQhq-mRuRIvU64dYaWsHI-y";

    // 2. N8N的Webhook地址（替换为你复制的N8N Production URL）
    private static final String N8N_WEBHOOK_URL = "http://8.210.230.220:5678/webhook/ding_talk";

    // 注入HttpServletRequest，用于获取请求头
    @PostMapping("/getMsg")
    public String handleRobotMsg(
            @RequestBody JSONObject requestBody,
            HttpServletRequest request // 注入request对象
    ) throws NoSuchAlgorithmException, InvalidKeyException {
        log.info("钉钉获取消息requestbody：" + requestBody.toJSONString());
        // 步骤1：验证钉钉请求合法性（不变）
        String postSign = request.getHeader("Sign");
        String postTimestamp = request.getHeader("Timestamp");
        String sign = generateSign(postTimestamp, DING_APP_SECRET);
        long currentTimestamp = System.currentTimeMillis();

        if (Math.abs(Long.parseLong(postTimestamp) - currentTimestamp) < 3600000 && sign.equals(postSign)) {
            // 步骤2：解析钉钉消息（不变）
            String senderStaffId = requestBody.getString("senderStaffId"); // 用户ID
            String userName = requestBody.getString("senderNick"); // 用户名
            String content = requestBody.getJSONObject("text").getString("content").trim(); // 消息内容
            String groupName = requestBody.getString("conversationTitle"); // 群聊名称

            log.info("钉钉获取消息,群聊：{},用户:{},获取内容:{}",groupName,userName,content);

            // 步骤3：转发消息到N8N的Webhook
            forwardToN8N(senderStaffId, userName, content, groupName);

            // 步骤4：返回空响应给钉钉（钉钉仅需接收成功，无需回复）
            return "success";
        } else {
            return "非法请求";
        }
//        forwardToN8N("1", "1", "目前库存中过期的产品和快过期产品是什么", "");
//        return "success";
    }

    /**
     * 生成钉钉签名（对应Python的hmac+base64逻辑）
     */
    private String generateSign(String timestamp, String appSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + appSecret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmacBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBase64String(hmacBytes);
    }

    /**
     * 转发消息到N8N的Webhook
     */
    private void forwardToN8N(String userId, String userName, String content, String groupName) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        // 显式指定UTF-8编码
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));

        // 构造消息体
        Map<String, Object> n8nMsg = new HashMap<>();
        n8nMsg.put("userId", userId);
        n8nMsg.put("userName", userName);
        n8nMsg.put("content", content); // 此时content已正确解码为中文
        n8nMsg.put("groupName", groupName);
        n8nMsg.put("timestamp", System.currentTimeMillis());

        // 强制UTF-8序列化JSONN8nController
        String jsonStr = JSONObject.toJSONString(n8nMsg);
        byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> request = new HttpEntity<>(jsonBytes, headers);

        // 发送请求
        String n8nResponse = restTemplate.postForObject(N8N_WEBHOOK_URL, request, String.class);
        log.info("转发到N8N成功，内容：" + content + "，N8N响应：" + n8nResponse);
    }
}
