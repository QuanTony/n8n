package com.project.n8n.controller;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Api(value = "n8n", description = "n8n")
@RestController
@RequestMapping("/api/n8n")
@CrossOrigin(origins = "*")
public class N8nController {

    private static final Logger logger = LoggerFactory.getLogger(N8nController.class);

    private String n8nWebhookUrl = "http://192.168.136.128:5678/webhook/fab358eb-9861-4781-8d3a-2121ddb4e8a9/chat";

    private final RestTemplate restTemplate;

    public N8nController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryDatabase(@RequestBody QueryRequest request) {
        logger.info("收到查询请求: {}", request.getQuery());

        Map<String, Object> response = new HashMap<>();

        try {
            // 准备请求体，符合n8n Chat Trigger节点的格式
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chatInput", request.getQuery());
            requestBody.put("sessionId", "springboot-session-" + System.currentTimeMillis());
            requestBody.put("action", "message");

            logger.info("调用n8n webhook: {}", n8nWebhookUrl);

            // 调用n8n webhook
            ResponseEntity<Map> n8nResponse = restTemplate.postForEntity(
                    n8nWebhookUrl,
                    requestBody,
                    Map.class
            );

            if (n8nResponse.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> n8nResult = n8nResponse.getBody();
                logger.info("n8n响应: {}", n8nResult);

                response.put("success", true);
                response.put("message", "查询成功");

                // 从n8n响应中提取结果
                if (n8nResult != null) {
                    // 根据n8n工作流的输出结构提取数据
                    // 工作流最后输出的是自然语言总结，我们需要提取这个
                    Object output = extractResultFromN8nResponse(n8nResult);
                    response.put("result", output);
                }

                return ResponseEntity.ok(response);
            } else {
                logger.error("n8n调用失败，状态码: {}", n8nResponse.getStatusCode());
                response.put("success", false);
                response.put("message", "n8n服务调用失败，状态码: " + n8nResponse.getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            logger.error("查询过程中发生错误", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 从n8n响应中提取结果
     * 根据工作流结构，最后一个节点"sql to natural language"会输出自然语言总结
     */
    private Object extractResultFromN8nResponse(Map<String, Object> n8nResponse) {
        try {
            // n8n响应通常包含一个数组，每个元素对应一个节点的输出
            if (n8nResponse.containsKey("data")) {
                Object data = n8nResponse.get("data");
                if (data instanceof Map) {
                    Map<?, ?> dataMap = (Map<?, ?>) data;
                    // 尝试查找包含结果的数据
                    for (Object value : dataMap.values()) {
                        if (value instanceof Map) {
                            Map<?, ?> item = (Map<?, ?>) value;
                            if (item.containsKey("json")) {
                                Object json = item.get("json");
                                if (json instanceof Map) {
                                    Map<?, ?> jsonMap = (Map<?, ?>) json;
                                    if (jsonMap.containsKey("output")) {
                                        return jsonMap.get("output");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 如果找不到标准结构，返回整个响应用于调试
            return n8nResponse;

        } catch (Exception e) {
            logger.warn("提取n8n结果时发生错误", e);
            return n8nResponse;
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "n8n-frontend-integration");
        response.put("timestamp", System.currentTimeMillis());
        response.put("n8nWebhookUrl", n8nWebhookUrl);
        return ResponseEntity.ok(response);
    }

    // 请求体类
    public static class QueryRequest {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
