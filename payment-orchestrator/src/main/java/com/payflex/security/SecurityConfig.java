package com.payflex.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http
            // permitir TODO sin autenticación
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
            )
            // desactivar CSRF para facilitar pruebas con GET/POST desde cualquier lado
            .csrf(csrf -> csrf.disable())
            // no intentes actuar como resource server OAuth2
            .oauth2ResourceServer(oauth2 -> oauth2.disable())
            // desactivar login form básico, etc.
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable());

    return http.build();
  }
}
