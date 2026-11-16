package com.smartstock.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // Serves templates/login.html for /login and any
    // /login?something address (error, logout, session...).
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}