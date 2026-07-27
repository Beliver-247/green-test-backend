package com.greentest.core;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String getGreeting() {
        return "Hello from the Core Module!";
    }
}
