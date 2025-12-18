package com.project.n8n.controller;

import io.swagger.annotations.Api;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Api(value = "test", description = "test")
@Controller
@RequestMapping("/test")
public class TestController {

    /**
     * 跳转n8n页面
     * @return
     */
    @GetMapping("/n8n")
    public String n8n() {
        return "n8n";
    }



}
