package com.ondo.wholesale.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 배분 취소 계약 스텁 (MUL-82) — 원본: api-lite/04_주문/DELETE_packings_{packingId}.md. */
@Tag(name = "04 주문")
@RestController
public class PackingController {

    @Operation(summary = "배분 취소 (카드 통째)", description = """
            배분이 풀린다 — `allocatedQty`가 줄고 가용재고가 돌아오며, 해소했던 미송이 되살아난다.
            부분 취소는 없다. PACKED 는 출고 묶음 해제(unpack) 후에만 취소할 수 있다.

            에러: 404 `RESOURCE_NOT_FOUND` (이미 취소 포함) / 409 `DOCUMENT_FINALIZED`""")
    @DeleteMapping("/api/wholesale/packings/{packingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelPacking(@PathVariable Long packingId) {
    }
}
