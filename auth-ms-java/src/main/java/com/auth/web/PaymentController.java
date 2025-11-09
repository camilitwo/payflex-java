package com.auth.web;


import com.auth.dto.PaymentDTO;
import com.auth.service.FlowService;
import com.auth.util.JwtUtils;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/flow")
public class PaymentController {

    private final FlowService flowService;

    public PaymentController(FlowService flowService) {
        this.flowService = flowService;
    }

    // 1) Front llama aquí para iniciar pago - Acepta JSON y form-urlencoded
    @PostMapping("/checkout")
    public Mono<ResponseEntity<?>> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) String subject,
            @RequestBody(required = false) CreatePaymentRequest jsonRequest) {

        // Extraer merchantId del JWT
        String merchantId = null;
        if (jwt != null) {
            merchantId = JwtUtils.extractMerchantId(jwt);
        }

        // Si no hay merchantId en el JWT, usar un valor por defecto o rechazar
        if (merchantId == null || merchantId.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .<Object>body(new ErrorResponse("No se pudo identificar el merchant. JWT inválido o ausente.")));
        }

        // Priorizar JSON si viene en el body
        String finalEmail = (jsonRequest != null && jsonRequest.getEmail() != null)
                ? jsonRequest.getEmail() : email;
        Long finalAmount = (jsonRequest != null && jsonRequest.getAmount() != null)
                ? jsonRequest.getAmount() : amount;
        String finalSubject = (jsonRequest != null && jsonRequest.getSubject() != null)
                ? jsonRequest.getSubject() : subject;

        // Validación manual simple
        if (finalEmail == null || finalEmail.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().<Object>body(new ErrorResponse("Email es requerido")));
        }
        if (finalAmount == null || finalAmount < 1) {
            return Mono.just(ResponseEntity.badRequest().<Object>body(new ErrorResponse("El monto debe ser mayor a 0")));
        }
        if (finalSubject == null || finalSubject.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().<Object>body(new ErrorResponse("El asunto es requerido")));
        }
        if (!finalEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return Mono.just(ResponseEntity.badRequest().<Object>body(new ErrorResponse("Email inválido")));
        }

        // Llamar al servicio de forma reactiva
        return flowService.createPayment(merchantId, finalEmail, finalAmount, finalSubject)
                .<ResponseEntity<?>>map(flowUrl -> ResponseEntity.ok().body(new CreatePaymentResponse(flowUrl)))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ErrorResponse("Error creando el pago: " + e.getMessage()))
                ));
    }

    // DTOs
    @Data
    public static class CreatePaymentRequest {
        private String email;
        private Long amount;
        private String subject;
    }

    public record ErrorResponse(String error) {}

    // 2) Webhook llamado por Flow (urlConfirmation)
    @PostMapping("/confirmation")
    public Mono<ResponseEntity<String>> confirmation(@RequestParam("token") String token) {
        return flowService.handleConfirmation(token)
                .map(payment -> ResponseEntity.ok("OK"))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: " + e.getMessage())));
    }

    // 3) Endpoint para que el front consulte estado de orden
    @GetMapping("/status/{commerceOrder}")
    public Mono<ResponseEntity<PaymentDTO>> getStatus(@PathVariable String commerceOrder) {
        return flowService.getPayment(commerceOrder)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // DTO simple para respuesta
    public record CreatePaymentResponse(String flowUrl) {
    }
}
