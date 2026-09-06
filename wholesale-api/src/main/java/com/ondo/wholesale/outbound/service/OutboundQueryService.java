package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.order.repository.PartnerRepository;
import com.ondo.wholesale.outbound.domain.Outbound;
import com.ondo.wholesale.outbound.dto.OutboundDetailResponse;
import com.ondo.wholesale.outbound.dto.OutboundItemResponse;
import com.ondo.wholesale.outbound.dto.OutboundRetailerResponse;
import com.ondo.wholesale.outbound.dto.OutboundSummaryResponse;
import com.ondo.wholesale.outbound.repository.OutboundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 출고 목록(소매처 헤더·봉투 목록)·상세 조회 (MUL-49).
 *
 * <p>포장 완료 탭(NOT_SHIPPED)과 출고 완료 탭(SHIPPED)이 같은 경로를 쓴다 — 기간
 * 필터의 축만 다르다(SHIPPED 는 shippedAt, 그 외 createdAt). 헤더의 집계와 봉투
 * 목록이 같은 필터 어휘를 공유해야 headerCount 와 펼침 행 수가 맞는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboundQueryService {

    private final OutboundRepository outboundRepository;
    private final PartnerRepository partnerRepository;
    private final OutboundReader reader;
    private final NamedParameterJdbcTemplate jdbc;

    public ApiResponse<List<OutboundRetailerResponse>> retailers(Long wholesalerId,
                                                                 OutboundListQuery query) {
        OutboundReader.RetailerPage page = reader.retailerPage(wholesalerId, query);
        List<OutboundRetailerResponse> rows = page.rows().stream()
                .map(row -> new OutboundRetailerResponse(
                        row.retailerId(), row.retailerName(), row.outboundCount(),
                        row.totalQty(), row.lastCreatedAt(), row.lastShippedAt()))
                .toList();
        int totalPages = (int) Math.ceil((double) page.total() / query.size());
        return ApiResponse.paged(rows, new ApiResponse.PageMeta(
                query.page(), query.size(), page.total(), totalPages));
    }

    public ApiResponse<List<OutboundSummaryResponse>> list(Long wholesalerId, OutboundListQuery query) {
        requireTradedRetailer(wholesalerId, query.retailerId());
        // Specification.allOf 는 null 요소를 거부한다 — 조건이 있을 때만 담는다
        List<Specification<Outbound>> conditions = new ArrayList<>(
                List.of(OutboundSpecs.ownedBy(wholesalerId)));
        if (query.status() != null) {
            conditions.add(OutboundSpecs.status(query.status()));
        }
        if (query.retailerId() != null) {
            conditions.add(OutboundSpecs.retailerScoped(query.retailerId()));
        }
        if (query.q() != null && !query.q().isBlank()) {
            conditions.add(OutboundSpecs.searchProduct(query.q()));
        }
        if (query.from() != null || query.to() != null) {
            conditions.add(OutboundSpecs.periodBetween(query.status(), query.from(), query.to()));
        }

        Page<Outbound> outbounds = outboundRepository.findAll(Specification.allOf(conditions),
                PageRequest.of(query.page(), query.size(), query.sort()));
        Map<Long, OutboundReader.Summary> summaries = reader.summaries(
                outbounds.getContent().stream().map(Outbound::getId).toList());

        List<OutboundSummaryResponse> rows = outbounds.getContent().stream()
                .map(outbound -> summaryRow(outbound, summaries.get(outbound.getId())))
                .toList();
        return ApiResponse.paged(rows, new ApiResponse.PageMeta(
                query.page(), query.size(), outbounds.getTotalElements(), outbounds.getTotalPages()));
    }

    public OutboundDetailResponse detail(Long wholesalerId, Long outboundId) {
        Outbound outbound = outboundRepository.findByIdAndWholesalerId(outboundId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("출고가 없거나 접근할 수 없습니다."));
        Partner partner = partnerRepository.findById(outbound.getPartnerId()).orElseThrow();
        List<OutboundItemResponse> items = reader.skuItems(outboundId);
        int totalQty = items.stream().mapToInt(OutboundItemResponse::qty).sum();
        // 버튼 활성 판정용 — 재고 검증이 없어 true 여도 확정이 실패할 수 있다
        boolean shippable = outbound.getShippedAt() == null && !items.isEmpty();
        return new OutboundDetailResponse(
                outbound.getId(), outbound.getOutboundNumber(),
                partner.getRetailerId(), partner.getRetailerName(),
                outbound.getCreatedAt(), outbound.getShippedAt(), outbound.getStatementNumber(),
                shippable, totalQty, items, reader.packingRefs(outboundId));
    }

    private OutboundSummaryResponse summaryRow(Outbound outbound, OutboundReader.Summary summary) {
        // 전 항목이 배분취소된 봉투는 요약이 비어 있다 — 0장으로 내린다
        return new OutboundSummaryResponse(
                outbound.getId(), outbound.getOutboundNumber(),
                summary == null ? null : summary.firstProductName(),
                summary == null ? 0 : summary.additionalItemCount(),
                summary == null ? null : summary.receiveBy(),
                outbound.getCreatedAt(), outbound.getShippedAt(), outbound.getStatementNumber(),
                summary == null ? 0 : summary.totalQty());
    }

    /** 거래 이력 없는 retailerId 는 404 — 필터에 안 걸린 200 빈 배열과 구분한다. */
    private void requireTradedRetailer(Long wholesalerId, Long retailerId) {
        if (retailerId == null) {
            return;
        }
        Integer traded = jdbc.queryForObject("""
                select count(*) from wholesale.partner
                where wholesaler_id = :wholesalerId and retailer_id = :retailerId
                """, new MapSqlParameterSource()
                        .addValue("wholesalerId", wholesalerId)
                        .addValue("retailerId", retailerId),
                Integer.class);
        if (traded == null || traded == 0) {
            throw new ResourceNotFoundException("거래 이력이 없는 소매처입니다.");
        }
    }
}
