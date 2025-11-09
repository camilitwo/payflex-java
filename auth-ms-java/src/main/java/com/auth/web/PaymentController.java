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

@RestController
@RequestMapping("/api/flow")
public class PaymentController {

    private final FlowService flowService;

    public PaymentController(FlowService flowService) {
        this.flowService = flowService;
    }

    // 1) Front llama aquí para iniciar pago - Acepta JSON y form-urlencoded
    @PostMapping("/checkout")
    public ResponseEntity<?> createPayment(
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("No se pudo identificar el merchant. JWT inválido o ausente."));
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
            return ResponseEntity.badRequest().body(new ErrorResponse("Email es requerido"));
        }
        if (finalAmount == null || finalAmount < 1) {
            return ResponseEntity.badRequest().body(new ErrorResponse("El monto debe ser mayor a 0"));
        }
        if (finalSubject == null || finalSubject.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("El asunto es requerido"));
        }
        if (!finalEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email inválido"));
        }

        try {
            String flowUrl = flowService.createPayment(merchantId, finalEmail, finalAmount, finalSubject);
            return ResponseEntity.ok().body(new CreatePaymentResponse(flowUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error creando el pago: " + e.getMessage()));
        }
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
    public ResponseEntity<String> confirmation(@RequestParam("token") String token) throws Exception {
        flowService.handleConfirmation(token);
        // Flow solo necesita 200 OK
        return ResponseEntity.ok("OK");
    }

    // 3) Endpoint para que el front consulte estado de orden
    @GetMapping("/status/{commerceOrder}")
    public ResponseEntity<PaymentDTO> getStatus(@PathVariable String commerceOrder) {
        PaymentDTO payment = flowService.getPayment(commerceOrder);
        return ResponseEntity.ok(payment);
    }

    // DTO simple para respuesta
    public record CreatePaymentResponse(String flowUrl) {
    }
}
