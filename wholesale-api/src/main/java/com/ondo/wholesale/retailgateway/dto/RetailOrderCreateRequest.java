package com.ondo.wholesale.retailgateway.dto;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;

import java.util.List;

/**
 * 소매 백엔드의 주문 생성 요청 (api/08_소매접점.md §2.2).
 *
 * <p>{@code retailOrderId}·{@code retailerId}는 소매 DB 의 외부 식별자다(FK 아님, D-051·D-052).
 * {@code wholesalerId}는 소매가 보내고 도매가 대조한다 — 유도하지 않는 이유는 소매 버그를
 * 조용히 삼키지 않기 위함. 중복 주문은 UNIQUE(retailOrderId, wholesalerId)가 막는다.
 *
 * <p><b>스냅샷 넷은 MUL-98 에서 더했다.</b> 접수를 실구현하면서 도매 DB 가 요구하는데
 * 계약에 칸이 없던 값들이다. {@code retailerName} 은 {@code partner.retailer_name} 이
 * NOT NULL 이라 <b>없으면 첫 거래의 접수 자체가 실패한다.</b> 나머지 셋은 없어도 돌아가지만
 * 도매 화면에 소매처 연락처와 수령인이 영영 안 뜬다 — V6 주석과 orders DDL 주석 둘 다
 * "소매 주문 접수 API 로 넘어온다" 고 적어뒀던 값이다.
 *
 * <p>도매가 소매 DB 를 못 읽어서 스냅샷으로 받는다. 나중에 소매처가 상호를 바꿔도
 * 지난 주문의 장끼에는 그때 이름이 남아야 한다 (U-15).
 *
 * @param retailerName  소매 상호. partner 를 만들 때 쓴다. 첫 거래가 아니면 기존 값을 그대로 둔다
 * @param retailerPhone 소매 연락처. 도매가 소매처에 전화할 때 쓴다 (V6)
 * @param agentName     사입삼촌. 장끼에 수령인으로 찍힌다. 직접 수령이면 null
 * @param agentPhone    사입삼촌 연락처. 직접 수령이면 null
 */
public record RetailOrderCreateRequest(
        Long retailOrderId,
        Long retailerId,
        Long wholesalerId,
        String retailerName,
        String retailerPhone,
        PaymentMethod expectedPaymentMethod,
        ReceiveBy receiveBy,
        String agentName,
        String agentPhone,
        List<RetailOrderItemRequest> items
) {}
