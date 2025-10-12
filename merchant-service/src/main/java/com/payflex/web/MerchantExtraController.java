package com.payflex.web;
import com.payflex.client.AuthClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class MerchantExtraController {
  private final AuthClient authClient;
  public MerchantExtraController(AuthClient authClient){ this.authClient = authClient; }
  @GetMapping("/ping-auth")
  @CircuitBreaker(name="authClient", fallbackMethod="fallback")
  public String pingAuth(){ return "auth says: " + authClient.hello(); }
  public String fallback(Throwable t){ return "auth unavailable"; }
}
