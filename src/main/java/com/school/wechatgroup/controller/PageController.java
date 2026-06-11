package com.school.wechatgroup.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面视图控制器 —— 负责渲染 Thymeleaf 模板。
 * 使用 @Controller（非 @RestController），以便返回视图名而非 JSON。
 */
@Controller
public class PageController {

    @GetMapping({"/login", "/login.html"})
    public String loginPage() {
        return "login";
    }

    @GetMapping({"/index", "/index.html"})
    public String indexPage() {
        return "index";
    }
}
