package com.aamir.service;

import com.aamir.exception.ServiceCustomException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DummyService {

    public Map<String, Object> getProfileData(String userId) {
        sleep(2000);
        return Map.of(
                "userId", userId,
                "name", "John Doe",
                "email", "john@example.com"
        );
    }

    public Map<String, Object> getOrderData(String userId) {
        sleep(2000);
        return Map.of(
                "userId", userId,
                "orders", List.of("ORD-001", "ORD-002", "ORD-003"),
                "totalOrders", 3
        );
    }

    public Map<String, Object> getPaymentData(String userId) {
        sleep(2000);
        return Map.of(
                "userId", userId,
                "balance", 1500.00,
                "currency", "USD"
        );
    }

    public String getPrimaryPrice(String symbol) {
        sleep(2000);
        return "Price{symbol=%s, value=150.25, source=primary}".formatted(symbol);
    }

    public String getBackupPrice(String symbol) {
        sleep(3000);
        return "Price{symbol=%s, value=150.30, source=backup}".formatted(symbol);
    }

    public Map<String, Object> alwaysFail(String userId) {
        sleep(1000);
        throw new ServiceCustomException(
                "Service unavailable for user: %s".formatted(userId),
                503
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceCustomException("Thread interrupted", 500, e);
        }
    }
}
