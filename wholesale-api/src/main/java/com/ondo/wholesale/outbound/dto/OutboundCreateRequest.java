package com.ondo.wholesale.outbound.dto;

import java.util.List;

/**
 * 포장 완료 요청 (api-lite/06_출고/POST_outbounds.md). 체크한 SKU 행들을 한 봉투로 묶는다.
 *
 * <p>{@code packingId}가 아니라 {@code packingItemId}인 이유 — 한 포장의 일부만 체크할 수
 * 있다(부분 출고, 포장 분할). 소매처는 담지 않고 서버가 항목에서 끌어내되 전부 같은
 * 소매처·같은 수령 방식이어야 한다. 재고는 아직 줄지 않는다 — 차감은 출고 확정.
 */
public record OutboundCreateRequest(List<Long> packingItemIds) {}
