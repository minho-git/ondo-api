package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.common.time.KstDays;
import com.ondo.wholesale.settlement.LedgerEntryType;
import com.ondo.wholesale.settlement.LedgerSign;
import com.ondo.wholesale.settlement.dto.LedgerEntryResponse;
import com.ondo.wholesale.settlement.dto.ReceivableLedgerResponse;
import com.ondo.wholesale.settlement.dto.ReceivableRetailerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 정산 탭 조회 (MUL-126) — 거래처별 미수 목록과 거래처 하나의 미수원장.
 *
 * <p>부호는 저장(플러스 = 갚을 돈이 늘었다)과 화면 계약(판매 −, 입금 +, 잔액 음수 = 채무)이 반대라
 * 내보낼 때 {@link LedgerSign}으로 뒤집는다 — 뒤집는 곳은 거기 하나다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivableQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 정렬 화이트리스트 — 요청 키를 ORDER BY 식으로만 바꾼다(문자열 조립 방어). */
    private static final Map<String, String> RETAILER_SORT = Map.of(
            // 화면 잔액은 저장값의 반대라 오름차순 = 빚이 큰 거래처부터
            "ledgerBalance", "-t.receivable_balance",
            "lastOccurredAt", "t.last_occurred_at",
            "retailerName", "t.retailer_name");

    private static final Map<LedgerEntryType, String> STORED_TYPE = Map.of(
            LedgerEntryType.SALE, "OUTBOUND",
            LedgerEntryType.PAYMENT, "PAYMENT",
            LedgerEntryType.PAYMENT_VOID, "PAYMENT_VOID",
            LedgerEntryType.ADJUST, "ADJUST");

    private static final Map<String, LedgerEntryType> SCREEN_TYPE = Map.of(
            "OUTBOUND", LedgerEntryType.SALE,
            "PAYMENT", LedgerEntryType.PAYMENT,
            "PAYMENT_VOID", LedgerEntryType.PAYMENT_VOID,
            "ADJUST", LedgerEntryType.ADJUST);

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 거래처별 미수 — 확정 주문이 있거나 돈이 오간 거래처만. 잔액은 원장 쓰기가 같이 갱신하는
     * {@code partner.receivable_balance}에서 읽는다(정렬 · 페이지를 원장 합산 없이 하려고 둔 칸).
     */
    public ApiResponse<List<ReceivableRetailerResponse>> retailers(Long wholesalerId, int page, int size,
                                                                  String sort) {
        checkPage(page, size);
        String orderBy = orderBy(sort, "ledgerBalance,asc", RETAILER_SORT);
        String source = """
                select * from (
                    select pt.id, pt.retailer_id, pt.retailer_name, pt.receivable_balance,
                           (select count(*) from wholesale.orders o
                            where o.partner_id = pt.id and o.status = 'CONFIRMED') as order_count,
                           (select max(l.occurred_at) from wholesale.receivable_ledger l
                            where l.partner_id = pt.id) as last_occurred_at
                    from wholesale.partner pt
                    where pt.wholesaler_id = :wholesalerId
                ) t
                where t.order_count > 0 or t.last_occurred_at is not null
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("wholesalerId", wholesalerId)
                .addValue("limit", size).addValue("offset", (long) page * size);

        long total = jdbc.queryForObject("select count(*) from (" + source + ") c", params, Long.class);
        List<ReceivableRetailerResponse> rows = jdbc.query(
                source + " order by " + orderBy + " nulls last, t.id limit :limit offset :offset", params,
                (rs, i) -> new ReceivableRetailerResponse(
                        rs.getLong("retailer_id"),
                        null,
                        rs.getString("retailer_name"),
                        rs.getInt("order_count"),
                        LedgerSign.toWholesaleScreen(rs.getLong("receivable_balance")),
                        rs.getObject("last_occurred_at", OffsetDateTime.class)));
        return ApiResponse.paged(rows, new ApiResponse.PageMeta(page, size, total, totalPages(total, size)));
    }

    /**
     * 거래처 하나의 미수원장. 거래 이력이 없어도 404 가 아니라 빈 목록 + 잔액 0 이다(계약).
     * {@code meta.ledgerBalance}는 필터 · 페이지와 무관한 현재 잔액이다.
     */
    public ReceivableLedgerResponse ledger(Long wholesalerId, Long retailerId, String entryType,
                                           LocalDate from, LocalDate to, int page, int size, String sort) {
        if (retailerId == null) {
            throw ApiException.validationFailed("retailerId", "필수입니다.");
        }
        checkPage(page, size);
        LedgerEntryType type = parseType(entryType);
        String direction = parseLedgerSort(sort);

        List<long[]> partners = jdbc.query("""
                select id, receivable_balance from wholesale.partner
                where wholesaler_id = :wholesalerId and retailer_id = :retailerId
                """, new MapSqlParameterSource()
                        .addValue("wholesalerId", wholesalerId).addValue("retailerId", retailerId),
                (rs, i) -> new long[]{rs.getLong("id"), rs.getLong("receivable_balance")});
        if (partners.isEmpty()) {
            return new ReceivableLedgerResponse(List.of(),
                    new ReceivableLedgerResponse.LedgerMeta(page, size, 0, 0, 0));
        }

        StringBuilder where = new StringBuilder("l.partner_id = :partnerId");
        MapSqlParameterSource params = new MapSqlParameterSource("partnerId", partners.getFirst()[0])
                .addValue("limit", size).addValue("offset", (long) page * size);
        if (type != null) {
            where.append(" and l.entry_type = :entryType");
            params.addValue("entryType", STORED_TYPE.get(type));
        }
        if (from != null) {
            where.append(" and l.occurred_at >= :from");
            params.addValue("from", KstDays.start(from));
        }
        if (to != null) {
            where.append(" and l.occurred_at < :toNext");
            params.addValue("toNext", KstDays.startOfNext(to));
        }

        long total = jdbc.queryForObject(
                "select count(*) from wholesale.receivable_ledger l where " + where, params, Long.class);
        List<LedgerEntryResponse> rows = jdbc.query("""
                select l.id, l.entry_type, l.delta, l.balance_after, l.occurred_at,
                       l.order_id, o.order_number, l.payment_id
                from wholesale.receivable_ledger l
                left join wholesale.orders o on o.id = l.order_id
                where %s
                order by l.occurred_at %s, l.id %s
                limit :limit offset :offset
                """.formatted(where, direction, direction), params,
                (rs, i) -> new LedgerEntryResponse(
                        rs.getLong("id"),
                        SCREEN_TYPE.get(rs.getString("entry_type")),
                        LedgerSign.toWholesaleScreen(rs.getLong("delta")),
                        LedgerSign.toWholesaleScreen(rs.getLong("balance_after")),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("order_id", Long.class),
                        rs.getObject("order_number", Integer.class),
                        rs.getObject("payment_id", Long.class)));
        return new ReceivableLedgerResponse(rows, new ReceivableLedgerResponse.LedgerMeta(
                page, size, total, totalPages(total, size),
                LedgerSign.toWholesaleScreen(partners.getFirst()[1])));
    }

    private static void checkPage(int page, int size) {
        if (page < 0) {
            throw ApiException.validationFailed("page", "0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.validationFailed("size", "1~" + MAX_PAGE_SIZE + " 이어야 합니다.");
        }
    }

    private static int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private static String orderBy(String raw, String fallback, Map<String, String> keys) {
        String value = (raw == null || raw.isBlank()) ? fallback : raw;
        String[] parts = value.split(",", 2);
        String column = keys.get(parts[0].trim());
        String direction = direction(parts);
        if (column == null || direction == null) {
            throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
        }
        return column + " " + direction;
    }

    /** 원장은 시간순 하나뿐이다 — 기본 최신순, {@code occurredAt,asc}로 오래된 순. */
    private static String parseLedgerSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return "desc";
        }
        String[] parts = raw.split(",", 2);
        String direction = direction(parts);
        if (!"occurredAt".equals(parts[0].trim()) || direction == null) {
            throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
        }
        return direction;
    }

    private static String direction(String[] parts) {
        if (parts.length < 2) {
            return "asc";
        }
        String value = parts[1].trim();
        return "desc".equalsIgnoreCase(value) ? "desc" : "asc".equalsIgnoreCase(value) ? "asc" : null;
    }

    private static LedgerEntryType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LedgerEntryType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.validationFailed("entryType", "정의되지 않은 원장 구분: " + raw);
        }
    }
}
