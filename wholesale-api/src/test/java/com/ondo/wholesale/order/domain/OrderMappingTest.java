package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.order.repository.BackorderRepository;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.repository.PartnerRepository;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 계열 엔티티가 V1 스키마와 맞물려 저장·재조회되는지 확인한다 (MUL-47).
 *
 * <p>스키마는 V1 에 이미 있고 엔티티만 새로 매핑했다 — ddl-auto=validate 라
 * 컨텍스트 부팅 자체가 컬럼 대조다. 여기서는 왕복(저장→flush/clear→재조회)으로
 * enum 문자열 저장과 null 허용 컬럼을 못박는다.
 */
@SpringBootTest
@Transactional
class OrderMappingTest extends PostgresTestSupport {

    @Autowired OrderRepository orderRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired BackorderRepository backorderRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private long wholesalerId;
    private long variantId;

    @BeforeEach
    void 재료를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "order-mapping@ondo.test", "9500000001");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        long productId = jdbc.queryForObject("""
                insert into wholesale.product (wholesaler_id, product_number, name, category_id)
                values (?, 1, '맵핑상품', ?) returning id
                """, Long.class, wholesalerId, leafId);
        long colorOptionId = jdbc.queryForObject("""
                insert into wholesale.color_option (product_id, color_id)
                values (?, ?) returning id
                """, Long.class, productId, colorId);
        variantId = jdbc.queryForObject("""
                insert into wholesale.variant (color_option_id, product_id, size, variant_seq)
                values (?, ?, 'FREE', 1) returning id
                """, Long.class, colorOptionId, productId);
    }

    @Test
    void 주문과_라인을_저장하고_다시_읽는다() {
        Partner partner = partnerRepository.save(Partner.builder()
                .wholesalerId(wholesalerId).retailerId(501L).retailerName("소매상A").build());
        Order order = Order.builder()
                .orderNumber(1)
                .partnerId(partner.getId())
                .wholesalerId(wholesalerId)
                .paymentTerm(PaymentMethod.CASH)
                .receiveMethod(ReceiveBy.RETAILER)
                .orderedAt(OffsetDateTime.now())
                .build();
        order.addItem(variantId, 3, 15000);
        order.addItem(variantId, 5, 15000);
        orderRepository.save(order);
        em.flush();
        em.clear();

        Order found = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(found.getPaymentTerm()).isEqualTo(PaymentMethod.CASH);
        assertThat(found.getReceiveMethod()).isEqualTo(ReceiveBy.RETAILER);
        assertThat(found.getConfirmedAt()).isNull();
        assertThat(found.getItems()).hasSize(2);
        OrderItem item = found.getItems().get(0);
        assertThat(item.getVariantId()).isEqualTo(variantId);
        assertThat(item.getQty()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualTo(15000);
        assertThat(item.getAllocatedQty()).isZero();
        assertThat(item.getShippedQty()).isZero();
    }

    @Test
    void 파트너의_전화번호_스냅샷을_저장하고_다시_읽는다() {
        Partner 있음 = partnerRepository.save(Partner.builder()
                .wholesalerId(wholesalerId).retailerId(502L).retailerName("소매상B")
                .retailerPhone("01012345678").build());
        Partner 없음 = partnerRepository.save(Partner.builder()
                .wholesalerId(wholesalerId).retailerId(503L).retailerName("소매상C").build());
        em.flush();
        em.clear();

        assertThat(partnerRepository.findById(있음.getId()).orElseThrow().getRetailerPhone())
                .isEqualTo("01012345678");
        assertThat(partnerRepository.findById(없음.getId()).orElseThrow().getRetailerPhone())
                .isNull();
    }

    @Test
    void 미송을_저장하면_기본_상태가_OPEN이다() {
        Partner partner = partnerRepository.save(Partner.builder()
                .wholesalerId(wholesalerId).retailerId(504L).retailerName("소매상D").build());
        Order order = Order.builder()
                .orderNumber(2).partnerId(partner.getId()).wholesalerId(wholesalerId)
                .paymentTerm(PaymentMethod.BANK_TRANSFER).receiveMethod(ReceiveBy.AGENT)
                .orderedAt(OffsetDateTime.now()).build();
        order.addItem(variantId, 4, 12000);
        orderRepository.save(order);
        em.flush();
        long orderItemId = order.getItems().get(0).getId();

        Backorder backorder = backorderRepository.save(
                Backorder.builder().orderItemId(orderItemId).qty(4).build());
        em.flush();
        em.clear();

        Backorder found = backorderRepository.findById(backorder.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(BackorderStatus.OPEN);
        assertThat(found.getQty()).isEqualTo(4);
        assertThat(found.getOrderItemId()).isEqualTo(orderItemId);
    }
}
