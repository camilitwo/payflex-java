package com.payflex.event.producer;

import com.payflex.dto.CreatePaymentIntentRequest;

public interface PaymentEventProducer {

    void publishPaymentApproved(CreatePaymentIntentRequest request);
}
