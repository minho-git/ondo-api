package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.settlement.dto.PaymentCreateRequest.PaymentAllocationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 멱등의 "같은 본문" 지문 — 고정 필드 순서로 이어 붙인 문자열의 SHA-256 hex (MUL-124 · MUL-125). */
final class RequestFingerprint {

    private final StringBuilder canonical = new StringBuilder();

    RequestFingerprint field(String name, Object value) {
        // 첫 필드 앞엔 구분자가 없다 — MUL-124 에 이미 저장된 입금 지문과 같은 문자열이어야 재요청이 맞는다
        if (!canonical.isEmpty()) {
            canonical.append('|');
        }
        canonical.append(name).append('=').append(value == null ? "" : value);
        return this;
    }

    /** 배분 줄 순서는 돈을 꺼내는 순서라 의미가 있다 — 보존한다. */
    RequestFingerprint lines(List<PaymentAllocationRequest> lines) {
        for (PaymentAllocationRequest line : lines) {
            canonical.append('|').append(line.orderId()).append(':').append(line.amount());
        }
        return this;
    }

    String sha256Hex() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 은 JVM 표준 알고리즘이다", e);
        }
    }
}
