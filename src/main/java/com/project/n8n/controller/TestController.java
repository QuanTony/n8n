package com.project.n8n.controller;

import io.swagger.annotations.Api;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai")
public class TestController {

    // 访问 /test/n8n 时，返回 templates/n8n.html
    @GetMapping("/aiReplay")
    public String n8n() {
        // 直接返回视图名（Freemarker会自动拼接 suffix: .html）
        return "aiReplay";
    }
}
