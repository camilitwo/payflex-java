package com.auth.web;


import com.auth.dto.PaymentDTO;
import com.auth.service.FlowService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flow")
public class PaymentController {

    private final FlowService flowService;

    public PaymentController(FlowService flowService) {
        this.flowService = flowService;
    }

    // 1) Front llama aquí para iniciar pago
    @PostMapping("/checkout")
    public ResponseEntity<?> createPayment(@RequestParam @Email String email,
                                           @RequestParam @Min(1) Long amount,
                                           @RequestParam @NotBlank String subject) {

        String flowUrl = flowService.createPayment(email, amount, subject);
        return ResponseEntity.ok().body(
                new CreatePaymentResponse(flowUrl)
        );
    }

    // 2) Webhook llamado por Flow (urlConfirmation)
    @PostMapping("/confirmation")
    public ResponseEntity<String> confirmation(@RequestParam("token") String token) throws Exception {
        PaymentDTO payment = flowService.handleConfirmation(token);
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
