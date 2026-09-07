package com.ondo.wholesale.outbound;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.OutboundFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 출고 목록·상세 API 통합 검증 (MUL-49).
 *
 * <p>바탕 픽스처 — 봉투 셋: OB1(가나상회, 8/10 생성, 포장 2개 — 니트 3+2 · 셔츠 4),
 * OB2(다라상회, 8/11 생성, 8/14 출고완료 — 니트 1), OB3(마바상회, 8/12 생성 — 셔츠 2).
 * created_at 은 DB default 라 jdbc 로 날짜를 박아 정렬·기간 축을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutboundListIntegrationTest extends PostgresTestSupport {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final OffsetDateTime 팔월10 = OffsetDateTime.of(2026, 8, 10, 10, 0, 0, 0, KST);
    private static final OffsetDateTime 팔월11 = OffsetDateTime.of(2026, 8, 11, 10, 0, 0, 0, KST);
    private static final OffsetDateTime 팔월12 = OffsetDateTime.of(2026, 8, 12, 10, 0, 0, 0, KST);
    private static final OffsetDateTime 팔월14 = OffsetDateTime.of(2026, 8, 14, 10, 0, 0, 0, KST);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long leafId;
    private long colorId;
    private long 가나상회;
    private long 니트;
    private long 셔츠;
    private long batchId;
    private long 출고1;
    private long 출고2;
    private long 출고3;

    @BeforeEach
    void 봉투들을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-list@ondo.test", "9500000037");
        leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9160);
        colorId = MasterDataFixture.색상을_넣는다(jdbc, 9260, 9261);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "셔츠", 2);
        가나상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 731L, "가나상회");
        long 다라상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 732L, "다라상회");
        long 마바상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 733L, "마바상회");
        batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);

        // OB1 — 가나상회, 포장 2개 (니트가 두 포장에 갈라져 있다: SKU 합산 재료)
        출고1 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 1);
        long 주문A = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 1, "RETAILER", 팔월10);
        long 니트라인A = OrderFixture.라인을_넣는다(jdbc, 주문A, 니트, 5, 1000, 3, 0);
        long 포장1 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문A, 출고1);
        OrderFixture.포장항목을_넣는다(jdbc, 포장1, 니트라인A, null, batchId, 3, false);
        long 주문B = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 2, "RETAILER", 팔월10);
        long 니트라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 니트, 2, 1000, 2, 0);
        long 셔츠라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 셔츠, 4, 2000, 4, 0);
        long 포장2 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문B, 출고1);
        OrderFixture.포장항목을_넣는다(jdbc, 포장2, 니트라인B, null, batchId, 2, false);
        OrderFixture.포장항목을_넣는다(jdbc, 포장2, 셔츠라인B, null, batchId, 4, false);

        // OB2 — 다라상회, 8/14 출고완료
        출고2 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 다라상회, 2);
        long 주문C = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 다라상회, 3, "RETAILER", 팔월11);
        long 니트라인C = OrderFixture.라인을_넣는다(jdbc, 주문C, 니트, 1, 1000, 1, 1);
        long 포장3 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문C, 출고2);
        OrderFixture.포장항목을_넣는다(jdbc, 포장3, 니트라인C, null, batchId, 1, false);
        OutboundFixture.출고를_확정한다(jdbc, 출고2, 1, 팔월14);

        // OB3 — 마바상회
        출고3 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 마바상회, 3);
        long 주문D = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 마바상회, 4, "AGENT", 팔월12);
        long 셔츠라인D = OrderFixture.라인을_넣는다(jdbc, 주문D, 셔츠, 2, 2000, 2, 0);
        long 포장4 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문D, 출고3);
        OrderFixture.포장항목을_넣는다(jdbc, 포장4, 셔츠라인D, null, batchId, 2, false);

        // created_at 은 DB default(now) — 정렬·기간 검증용으로 날짜를 박는다
        jdbc.update("update wholesale.outbound set created_at = ? where id = ?", 팔월10, 출고1);
        jdbc.update("update wholesale.outbound set created_at = ? where id = ?", 팔월11, 출고2);
        jdbc.update("update wholesale.outbound set created_at = ? where id = ?", 팔월12, 출고3);
    }

    @Test
    void 봉투목록은_meta를_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                // 기본 정렬 createdAt 내림차순 — 최신(OB3)이 먼저
                .andExpect(jsonPath("$.data[0].id").value(출고3))
                .andExpect(jsonPath("$.data[0].receiveBy").value("AGENT"))
                .andExpect(jsonPath("$.data[0].shippedAt").value((Object) null))
                .andExpect(jsonPath("$.data[1].id").value(출고2))
                .andExpect(jsonPath("$.data[1].statementNumber").value(1))
                .andExpect(jsonPath("$.data[1].shippedAt").isNotEmpty())
                // "니트 외 1건 · 9장" — 첫 항목 상품명과 SKU 종류 기준 외 N건
                .andExpect(jsonPath("$.data[2].id").value(출고1))
                .andExpect(jsonPath("$.data[2].outboundNumber").value(1))
                .andExpect(jsonPath("$.data[2].summaryProductName").value("니트"))
                .andExpect(jsonPath("$.data[2].additionalItemCount").value(1))
                .andExpect(jsonPath("$.data[2].totalQty").value(9));
    }

    @Test
    void 상태필터의_기간축이_출고완료면_shippedAt이다() throws Exception {
        // OB2: 생성 8/11 · 출고 8/14 — SHIPPED 탭에서 8/14 로 걸린다
        mvc.perform(get("/api/wholesale/outbounds")
                        .param("status", "SHIPPED").param("from", "2026-08-14").param("to", "2026-08-14")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(출고2));
        // 생성일(8/11)로는 안 걸린다 — 축이 shippedAt 이라서
        mvc.perform(get("/api/wholesale/outbounds")
                        .param("status", "SHIPPED").param("from", "2026-08-11").param("to", "2026-08-11")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        // 그 외 상태의 축은 createdAt 이다
        mvc.perform(get("/api/wholesale/outbounds")
                        .param("status", "NOT_SHIPPED").param("from", "2026-08-12").param("to", "2026-08-12")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(출고3));
    }

    @Test
    void 봉투목록은_소매처와_상품명으로_거른다() throws Exception {
        // retailerId 로 좁히면 그 소매처의 봉투만
        mvc.perform(get("/api/wholesale/outbounds").param("retailerId", "731")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(출고1));
        // q 는 봉투에 담긴 상품명으로 거른다 — 헤더도 같은 조건이라 건수가 맞는다
        mvc.perform(get("/api/wholesale/outbounds").param("q", "셔츠")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/wholesale/outbounds/retailers").param("q", "셔츠")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));
        // 거래 이력 없는 retailerId 는 404 — 필터에 안 걸린 빈 배열과 구분
        mvc.perform(get("/api/wholesale/outbounds").param("retailerId", "99999")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 소매처_페이징은_소매처를_경계에서_쪼개지_않는다() throws Exception {
        // 가나상회에 봉투를 하나 더 — 두 봉투가 한 헤더 행으로 뭉쳐야 한다
        long 출고4 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 4);
        long 주문E = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 5, "RETAILER", 팔월12);
        long 니트라인E = OrderFixture.라인을_넣는다(jdbc, 주문E, 니트, 1, 1000, 1, 0);
        long 포장5 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문E, 출고4);
        OrderFixture.포장항목을_넣는다(jdbc, 포장5, 니트라인E, null, batchId, 1, false);
        jdbc.update("update wholesale.outbound set created_at = ? where id = ?", 팔월14, 출고4);

        // 최근 활동 순: 가나(8/14) → 마바(8/12) → 다라(8/11). size=2 면 소매처 단위로 끊긴다
        mvc.perform(get("/api/wholesale/outbounds/retailers").param("size", "2")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].retailerId").value(731))
                .andExpect(jsonPath("$.data[0].retailerName").value("가나상회"))
                .andExpect(jsonPath("$.data[0].outboundCount").value(2))
                .andExpect(jsonPath("$.data[0].totalQty").value(10))
                .andExpect(jsonPath("$.data[1].retailerId").value(733));
        mvc.perform(get("/api/wholesale/outbounds/retailers").param("size", "2").param("page", "1")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].retailerId").value(732))
                .andExpect(jsonPath("$.data[0].lastShippedAt").isNotEmpty());
    }

    @Test
    void 상세items는_SKU단위로_합쳐진다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds/" + 출고1)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(출고1))
                .andExpect(jsonPath("$.data.retailerId").value(731))
                .andExpect(jsonPath("$.data.retailerName").value("가나상회"))
                // 두 포장에 갈라진 니트(3+2)가 한 행으로 합쳐진다
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].variantId").value(니트))
                .andExpect(jsonPath("$.data.items[0].qty").value(5))
                .andExpect(jsonPath("$.data.items[1].variantId").value(셔츠))
                .andExpect(jsonPath("$.data.items[1].qty").value(4))
                .andExpect(jsonPath("$.data.totalQty").value(9))
                // 주문 역추적용 포장 링크는 합치지 않는다
                .andExpect(jsonPath("$.data.packings.length()").value(2))
                .andExpect(jsonPath("$.data.packings[0].orderNumber").value(1))
                .andExpect(jsonPath("$.data.packings[1].orderNumber").value(2));
    }

    @Test
    void isShippable은_확정전_살아있는_항목이_있을때만_참이다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds/" + 출고1)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.isShippable").value(true));
        // 이미 확정된 봉투는 거짓
        mvc.perform(get("/api/wholesale/outbounds/" + 출고2)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.isShippable").value(false));
        // 살아있는 항목이 하나도 없으면(전 항목 배분취소) 거짓
        jdbc.update("""
                update wholesale.packing_item set deleted_at = now()
                where packing_id in (select id from wholesale.packing where outbound_id = ?)
                """, 출고3);
        mvc.perform(get("/api/wholesale/outbounds/" + 출고3)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.isShippable").value(false))
                .andExpect(jsonPath("$.data.totalQty").value(0));
    }

    @Test
    void 상세는_2XL_라벨_사이즈도_그대로_내린다() throws Exception {
        // DB 는 '2XL' 라벨로 저장한다 (SizeConverter) — enum 상수명(X2L)으로 읽으면 깨진다
        long 패딩 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "패딩", 3);
        jdbc.update("update wholesale.variant set size = '2XL' where id = ?", 패딩);
        long 출고5 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 5);
        long 주문F = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 6, "RETAILER", 팔월12);
        long 라인F = OrderFixture.라인을_넣는다(jdbc, 주문F, 패딩, 2, 1000, 2, 0);
        long 포장F = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문F, 출고5);
        OrderFixture.포장항목을_넣는다(jdbc, 포장F, 라인F, null, batchId, 2, false);

        mvc.perform(get("/api/wholesale/outbounds/" + 출고5)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].size").value("2XL"));
    }

    @Test
    void 남의_출고는_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-other@ondo.test", "9500000038");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 741L, "남의상회");
        long 남의출고 = OutboundFixture.출고를_넣는다(jdbc, 남, 남거래처, 1);

        mvc.perform(get("/api/wholesale/outbounds/" + 남의출고)
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
