package com.greentest.api;

import com.greentest.core.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    
    private final GreetingService greetingService;
    
    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }
    
    @GetMapping("/hello")
    public String hello() {
        System.out.println("greeting service says hello !");
        return greetingService.getGreeting();
    }
}
