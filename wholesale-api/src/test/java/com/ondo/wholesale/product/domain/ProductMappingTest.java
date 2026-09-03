package com.ondo.wholesale.product.domain;

import com.ondo.wholesale.product.repository.ProductRepository;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 애그리거트(product / color_option / variant)가 V1 스키마에 맞게 매핑됐는지 검증 (MUL-89).
 *
 * <p>상품 등록은 상품 1 + 색상옵션 N + variant N×M 을 한 번에 저장한다. 색상옵션과 variant 는
 * 상품에 딸린 것이라 <b>상품을 저장하면 같이 저장돼야</b> 한다. 사이즈 '2XL' 은 자바 상수명이
 * X2L 이라 컨버터가 DB 문자열로 되돌리는지 반드시 raw 컬럼으로 확인한다.
 */
@SpringBootTest
@Transactional
class ProductMappingTest extends PostgresTestSupport {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private long wholesalerId;
    private Color black;

    private long leafCategoryId;

    @BeforeEach
    void 마스터를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "mapping@ondo.test", "9100000001");
        leafCategoryId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9101);
        black = em.find(Color.class, MasterDataFixture.색상을_넣는다(jdbc, 9100, 9110));
    }

    @Test
    void 상품을_저장하면_색상옵션과_variant가_함께_저장된다() {
        Product product = 상품을_만든다(1);
        ColorOption option = product.addColorOption(black);
        option.addVariant(Size.S, product.nextVariantSeq());
        option.addVariant(Size.M, product.nextVariantSeq());

        Product saved = 저장하고_다시_읽는다(product);

        assertThat(saved.getColorOptions()).hasSize(1);
        ColorOption savedOption = saved.getColorOptions().get(0);
        assertThat(savedOption.getColor().getName()).isEqualTo("블랙");
        assertThat(savedOption.getVariants()).hasSize(2)
                .extracting(Variant::getVariantSeq).containsExactly(1, 2);
        assertThat(saved.getLastVariantSeq()).isEqualTo(2);
    }

    @Test
    void 사이즈_2XL이_DB에는_2XL_문자열로_저장된다() {
        Product product = 상품을_만든다(2);
        product.addColorOption(black).addVariant(Size.X2L, product.nextVariantSeq());
        productRepository.save(product);
        em.flush();

        // @Enumerated(STRING) 이었다면 상수명 X2L 이 나가 variant_size_ck 에 걸렸다
        String raw = jdbc.queryForObject(
                "select size from wholesale.variant where product_id = ?", String.class, product.getId());
        assertThat(raw).isEqualTo("2XL");
    }

    @Test
    void 같은_색상옵션에_같은_사이즈를_두_번_넣으면_막힌다() {
        Product product = 상품을_만든다(3);
        ColorOption option = product.addColorOption(black);
        option.addVariant(Size.S, product.nextVariantSeq());
        option.addVariant(Size.S, product.nextVariantSeq());

        // variant_size_uk (color_option_id, size) WHERE deleted_at IS NULL
        // IDENTITY 채번이라 save 시점에 바로 INSERT 가 나간다
        assertThatThrownBy(() -> {
            productRepository.save(product);
            em.flush();
        }).hasMessageContaining("variant_size_uk");
    }

    @Test
    void soft_delete된_사이즈는_같은_색에_다시_추가할_수_있다() {
        Product product = 상품을_만든다(4);
        ColorOption option = product.addColorOption(black);
        Variant first = option.addVariant(Size.S, product.nextVariantSeq());
        productRepository.save(product);
        em.flush();

        first.softDelete();
        em.flush();
        option.addVariant(Size.S, product.nextVariantSeq());
        em.flush(); // 부분 유니크 덕에 통과해야 한다

        assertThat(first.isAlive()).isFalse();
        Integer aliveCount = jdbc.queryForObject(
                "select count(*) from wholesale.variant where product_id = ? and deleted_at is null",
                Integer.class, product.getId());
        assertThat(aliveCount).isEqualTo(1);
    }

    @Test
    void 저장한_값이_그대로_읽힌다() {
        Product product = 상품을_만든다(5);
        product.addColorOption(black).addVariant(Size.FREE, product.nextVariantSeq());

        Product saved = 저장하고_다시_읽는다(product);
        Variant variant = saved.getColorOptions().get(0).getVariants().get(0);

        assertThat(saved.getName()).isEqualTo("오버핏 코튼 티셔츠");
        assertThat(saved.getCategoryId()).isEqualTo(leafCategoryId);
        assertThat(saved.getProductNumber()).isEqualTo(5);
        assertThat(variant.getSize()).isEqualTo(Size.FREE);
        assertThat(variant.getStockQty()).isZero();
        assertThat(variant.getReservedQty()).isZero();
        // numeric(16,6) 은 스케일까지 실려 온다 — 값으로 비교한다
        assertThat(variant.getAvgCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Product 상품을_만든다(int productNumber) {
        return Product.builder()
                .wholesalerId(wholesalerId)
                .productNumber(productNumber)
                .name("오버핏 코튼 티셔츠")
                .categoryId(leafCategoryId)
                .build();
    }

    private Product 저장하고_다시_읽는다(Product product) {
        productRepository.save(product);
        em.flush();
        em.clear();
        return productRepository.findById(product.getId()).orElseThrow();
    }
}
