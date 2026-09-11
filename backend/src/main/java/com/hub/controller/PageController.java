package com.hub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) return "redirect:/";
        return "login";
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

}
