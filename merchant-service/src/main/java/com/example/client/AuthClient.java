package com.example.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
@FeignClient(name="auth-ms-java", path="/")
public interface AuthClient { @GetMapping("/hello") String hello(); }
