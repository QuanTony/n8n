package com.project.n8n.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * n8n智能查询控制器
 * 适配n8n Webhook返回的JSON对象/数组格式，解决解析错误问题
 */
@Api(value = "n8n", description = "n8n智能查询接口")
@RestController
@RequestMapping("/api/n8n")
@CrossOrigin(origins = "*")
public class N8nController {

    private static final Logger logger = LoggerFactory.getLogger(N8nController.class);

    // n8n Webhook地址（替换为你的实际地址）
    private String n8nWebhookUrl = "http://8.210.230.220:5678/webhook/d492e45e-239a-4a7f-817f-4535975b91c1";

    // RestTemplate实例（优化初始化方式）
    private final RestTemplate restTemplate;

    public N8nController() {
        // 初始化RestTemplate，添加超时配置
        RestTemplate restTemplate = new RestTemplate();
        // 设置超时（可选，防止n8n响应过慢）
        /*
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 连接超时5秒
        factory.setReadTimeout(30000);   // 读取超时30秒
        restTemplate.setRequestFactory(factory);
        */
        this.restTemplate = restTemplate;
    }

    /**
     * 智能查询接口
     * @param request 查询请求体
     * @return 封装后的查询结果
     */
    @ApiOperation(value = "自然语言查询", notes = "调用n8n分析飞书数据并返回结果")
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryDatabase(@RequestBody QueryRequest request) {
        // 初始化响应结果
        Map<String, Object> response = new HashMap<>();

        // 校验入参
        String query = request.getQuery();
        if (query == null || query.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "查询问题不能为空！");
            return ResponseEntity.badRequest().body(response);
        }

        logger.info("开始处理查询请求: {}", query);

        try {
            // 1. 构建n8n请求体（适配Chat Trigger节点格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chatInput", query.trim());
            requestBody.put("sessionId", "springboot-session-" + System.currentTimeMillis());
            requestBody.put("action", "message");
            requestBody.put("timestamp", System.currentTimeMillis());

            logger.info("调用n8n Webhook: {}", n8nWebhookUrl);

            // 2. 调用n8n Webhook（关键：改为接收Object类型，兼容数组/对象）
            ResponseEntity<Object> n8nResponse = restTemplate.postForEntity(
                    n8nWebhookUrl,
                    requestBody,
                    Object.class
            );

            // 3. 处理n8n响应
            if (n8nResponse.getStatusCode().is2xxSuccessful()) {
                Object n8nResult = n8nResponse.getBody();
                logger.info("n8n响应结果: {}", n8nResult);

                // 4. 提取并封装结果
                Object finalResult = extractResultFromN8nResponse(n8nResult);
                response.put("success", true);
                response.put("message", "查询成功");
                response.put("result", finalResult);

                return ResponseEntity.ok(response);
            } else {
                // n8n返回非200状态码
                String errorMsg = String.format("n8n服务调用失败，状态码: %s", n8nResponse.getStatusCode());
                logger.error(errorMsg);
                response.put("success", false);
                response.put("message", errorMsg);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            // 全局异常捕获
            String errorMsg = "查询过程中发生异常: " + e.getMessage();
            logger.error(errorMsg, e);
            response.put("success", false);
            response.put("message", errorMsg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 从n8n响应中提取有效结果（兼容数组/对象格式）
     * @param n8nResponse n8n原始响应
     * @return 提取后的分析结果
     */
    private Object extractResultFromN8nResponse(Object n8nResponse) {
        // 空值处理
        if (n8nResponse == null) {
            return "n8n未返回有效数据";
        }

        try {
            // 情况1：n8n返回JSON对象（Webhook选First Entry JSON）
            if (n8nResponse instanceof Map) {
                Map<?, ?> resultMap = (Map<?, ?>) n8nResponse;

                // 优先提取Edit Fields节点的output字段
                if (resultMap.containsKey("output")) {
                    return resultMap.get("output");
                }
                // 兼容response字段（Set节点）
                else if (resultMap.containsKey("response")) {
                    return resultMap.get("response");
                }
                // 兼容json字段
                else if (resultMap.containsKey("json") && resultMap.get("json") instanceof Map) {
                    Map<?, ?> jsonMap = (Map<?, ?>) resultMap.get("json");
                    if (jsonMap.containsKey("output")) {
                        return jsonMap.get("output");
                    }
                }
                // 返回整个对象（调试用）
                return resultMap;
            }

            // 情况2：n8n返回JSON数组（旧配置）
            else if (n8nResponse instanceof List) {
                List<?> resultList = (List<?>) n8nResponse;
                if (!resultList.isEmpty()) {
                    // 取最后一个节点的输出
                    Object lastNodeOutput = resultList.get(resultList.size() - 1);
                    if (lastNodeOutput instanceof Map) {
                        Map<?, ?> lastMap = (Map<?, ?>) lastNodeOutput;
                        // 提取output字段
                        if (lastMap.containsKey("json") && lastMap.get("json") instanceof Map) {
                            Map<?, ?> jsonMap = (Map<?, ?>) lastMap.get("json");
                            if (jsonMap.containsKey("output")) {
                                return jsonMap.get("output");
                            }
                        }
                        // 直接返回最后一个节点数据
                        return lastMap;
                    }
                    // 返回数组第一条数据
                    return resultList.get(0);
                } else {
                    return "n8n返回空数组";
                }
            }

            // 情况3：其他类型（字符串/数字等）
            else {
                return n8nResponse.toString();
            }

        } catch (Exception e) {
            logger.warn("提取n8n结果时发生异常，返回原始数据", e);
            return n8nResponse;
        }
    }

    /**
     * 健康检查接口
     */
    @ApiOperation(value = "健康检查", notes = "检查n8n集成服务是否可用")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "n8n-frontend-integration");
        response.put("timestamp", System.currentTimeMillis());
        response.put("n8nWebhookUrl", n8nWebhookUrl);

        // 尝试连接n8n
        try {
            ResponseEntity<String> n8nHealth = restTemplate.getForEntity(
                    "http://8.210.230.220:5678/healthz",
                    String.class
            );
            response.put("n8nStatus", n8nHealth.getStatusCode().is2xxSuccessful() ? "UP" : "DOWN");
        } catch (Exception e) {
            response.put("n8nStatus", "DOWN");
            response.put("n8nError", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 查询请求体DTO
     */
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