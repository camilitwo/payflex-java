package com.payflex.web;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class HealthController {

  private final HealthEndpoint health;
  public HealthController(HealthEndpoint health){ this.health = health; }

  @GetMapping("/health")
  public ResponseEntity<?> health(){
    var h = health.health();
    var up = org.springframework.boot.actuate.health.Status.UP;
    return ResponseEntity.status(h.getStatus().equals(up)?200:503).body(h);
  }
}
