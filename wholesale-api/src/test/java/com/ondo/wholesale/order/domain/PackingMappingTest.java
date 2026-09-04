package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.order.repository.AllocationBatchRepository;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.repository.PackingRepository;
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
 * 포장·배분 엔티티가 V1 스키마와 맞물려 저장·재조회되는지 확인한다 (MUL-47).
 */
@SpringBootTest
@Transactional
class PackingMappingTest extends PostgresTestSupport {

    @Autowired OrderRepository orderRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired PackingRepository packingRepository;
    @Autowired AllocationBatchRepository allocationBatchRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private long wholesalerId;
    private long orderId;
    private long orderItemId;

    @BeforeEach
    void 재료를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "packing-mapping@ondo.test", "9500000002");
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
        long variantId = jdbc.queryForObject("""
                insert into wholesale.variant (color_option_id, product_id, size, variant_seq)
                values (?, ?, 'FREE', 1) returning id
                """, Long.class, colorOptionId, productId);

        Partner partner = partnerRepository.save(Partner.builder()
                .wholesalerId(wholesalerId).retailerId(601L).retailerName("소매상E").build());
        Order order = Order.builder()
                .orderNumber(1).partnerId(partner.getId()).wholesalerId(wholesalerId)
                .paymentTerm(PaymentMethod.CASH).receiveMethod(ReceiveBy.RETAILER)
                .orderedAt(OffsetDateTime.now()).build();
        order.addItem(variantId, 5, 10000);
        orderRepository.save(order);
        em.flush();
        orderId = order.getId();
        orderItemId = order.getItems().get(0).getId();
    }

    @Test
    void 포장과_항목을_저장하고_다시_읽는다() {
        AllocationBatch batch = allocationBatchRepository.save(
                AllocationBatch.builder().wholesalerId(wholesalerId).build());
        Packing packing = Packing.builder().orderId(orderId).build();
        packing.addItem(orderItemId, null, batch.getId(), 2);
        packingRepository.save(packing);
        em.flush();
        em.clear();

        Packing found = packingRepository.findById(packing.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(PackingStatus.READY);
        assertThat(found.getOrderId()).isEqualTo(orderId);
        assertThat(found.getOutboundId()).isNull();
        assertThat(found.getItems()).hasSize(1);
        PackingItem item = found.getItems().get(0);
        assertThat(item.getOrderItemId()).isEqualTo(orderItemId);
        assertThat(item.getBackorderId()).isNull();
        assertThat(item.getAllocationBatchId()).isEqualTo(batch.getId());
        assertThat(item.getQty()).isEqualTo(2);
        assertThat(item.getDeletedAt()).isNull();
    }
}
