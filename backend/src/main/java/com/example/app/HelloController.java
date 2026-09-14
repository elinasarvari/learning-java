package com.example.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class HelloController {

    @GetMapping("/api/hello")
    public String hello() {
        return "Hello Elina";
    }
}
