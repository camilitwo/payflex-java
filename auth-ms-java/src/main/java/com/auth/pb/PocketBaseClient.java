package com.auth.pb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

@Component
public class PocketBaseClient {

  private static final Logger log = LoggerFactory.getLogger(PocketBaseClient.class);

  private final WebClient http;
  private final PocketBaseProperties props;

  public PocketBaseClient(PocketBaseProperties props){
    this.props = props;
    if (props.getUrl() == null || props.getUrl().isBlank()) {
      log.error("[PocketBase] pocketbase.url no está configurado. Define POCKETBASE_URL en el entorno.");
      throw new IllegalStateException("pocketbase.url is not configured");
    }
    if (props.getCollection() == null || props.getCollection().isBlank()) {
      log.error("[PocketBase] pocketbase.collection no está configurado. Define POCKETBASE_COLLECTION en el entorno.");
      throw new IllegalStateException("pocketbase.collection is not configured");
    }

    // Configurar timeouts
    Duration responseTimeout = Duration.ofSeconds(15);

    HttpClient httpClient = HttpClient.create()
        .responseTimeout(responseTimeout);

    this.http = WebClient.builder()
        .baseUrl(props.getUrl())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();

    log.info("[PocketBase] Base URL={} Collection={} ResponseTimeout={}s",
        props.getUrl(), props.getCollection(), responseTimeout.getSeconds());
  }


  public Mono<Map<String,Object>> authWithPassword(String identity, String password){
    String path = "/api/collections/" + props.getCollection() + "/auth-with-password";
    log.info("🔵 [PocketBase] POST {} para identity={}", path, identity);
    return http.post().uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("identity", identity, "password", password))
        .retrieve()
        .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
            clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("No error details")
                .flatMap(errorBody -> {
                  log.error("🔴 [PocketBase] Auth failed status={} body={}", clientResponse.statusCode(), errorBody);
                  return Mono.error(new RuntimeException("PocketBase auth failed: " + errorBody));
                })
        )
        .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
        .timeout(Duration.ofSeconds(15))
        .doOnSuccess(res -> log.info("✅ [PocketBase] Auth exitoso para identity={}", identity))
        .doOnError(err -> log.error("🔴 [PocketBase] authWithPassword error: {}", err.getMessage(), err));
  }

  public Mono<Map<String,Object>> createUser(Map<String, Object> userData){
    String path = "/api/collections/" + props.getCollection() + "/records";
    log.info("🔵 [PocketBase] POST {} para email={}", path, userData.get("email"));
    return http.post().uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(userData)
        .retrieve()
        .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
            clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("No error details")
                .flatMap(errorBody -> {
                  log.error("🔴 [PocketBase] CreateUser failed status={} body={}", clientResponse.statusCode(), errorBody);
                  return Mono.error(new RuntimeException("PocketBase createUser failed: " + errorBody));
                })
        )
        .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
        .timeout(Duration.ofSeconds(15))
        .doOnSuccess(res -> log.info("✅ [PocketBase] Usuario creado id={}", res.get("id")))
        .doOnError(err -> log.error("🔴 [PocketBase] createUser error: {}", err.getMessage(), err));
  }
}
