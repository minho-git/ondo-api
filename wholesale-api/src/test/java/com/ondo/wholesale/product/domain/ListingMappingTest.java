package com.ondo.wholesale.product.domain;

import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ListingVariantRepository;
import com.ondo.wholesale.product.repository.ProductRepository;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 게시 도메인(listing / listing_image / listing_variant) 매핑 검증 (MUL-89).
 *
 * <p>이미지는 전체 교체 계약이라 orphanRemoval 로 기존 행이 지워져야 하고,
 * listing_variant 는 대리키 없는 복합 PK 라 @EmbeddedId 매핑이 실스키마와 맞는지 본다.
 */
@SpringBootTest
@Transactional
class ListingMappingTest extends PostgresTestSupport {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingVariantRepository listingVariantRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private Product product;
    private Variant variant;

    @BeforeEach
    void 상품을_심는다() {
        long wholesalerId = jdbc.queryForObject("""
                insert into wholesale.wholesaler (email, password_hash, biz_reg_no, biz_name, biz_owner_name)
                values ('listing@ondo.test', 'x', '9200000001', '테스트도매', '김테스트') returning id
                """, Long.class);
        jdbc.update("insert into common.category (id, parent_id, name, depth) values (9201, null, '여성', 1)");
        jdbc.update("insert into common.category (id, parent_id, name, depth) values (9202, 9201, '의류', 2)");
        jdbc.update("insert into common.category (id, parent_id, name, depth) values (9203, 9202, '상의', 3)");
        jdbc.update("insert into common.color_group (id, name) values (9200, '무채색')");
        jdbc.update("insert into common.color (id, group_id, name) values (9210, 9200, '블랙')");

        product = Product.builder()
                .wholesalerId(wholesalerId).productNumber(1)
                .name("오버핏 코튼 티셔츠").categoryId(9203L)
                .build();
        variant = product.addColorOption(em.find(Color.class, 9210L))
                .addVariant(Size.S, product.nextVariantSeq());
        productRepository.save(product);
        em.flush();
    }

    @Test
    void 게시글은_상품당_하나만_저장된다() {
        listingRepository.save(게시글을_만든다("첫 게시글"));
        em.flush();

        // listing_product_uk UNIQUE (product_id) — IDENTITY 라 save 시점에 INSERT 가 나간다
        assertThatThrownBy(() -> {
            listingRepository.save(게시글을_만든다("둘째 게시글"));
            em.flush();
        }).hasMessageContaining("listing_product_uk");
    }

    @Test
    void 이미지_교체는_기존_행을_지우고_새로_넣는다() {
        Listing listing = 게시글을_만든다("이미지 교체");
        listing.replaceImages(List.of("https://cdn.ondo.example/a.jpg", "https://cdn.ondo.example/b.jpg"));
        listingRepository.save(listing);
        em.flush();

        listing.replaceImages(List.of("https://cdn.ondo.example/c.jpg"));
        em.flush();

        List<String> rows = jdbc.queryForList(
                "select url from wholesale.listing_image where listing_id = ? order by sort_order",
                String.class, listing.getId());
        // orphanRemoval — a.jpg, b.jpg 행은 DB 에서도 사라져야 한다
        assertThat(rows).containsExactly("https://cdn.ondo.example/c.jpg");
    }

    @Test
    void 이미지_sortOrder는_목록_인덱스를_따른다() {
        Listing listing = 게시글을_만든다("이미지 순서");
        listing.replaceImages(List.of("https://cdn.ondo.example/1.jpg", "https://cdn.ondo.example/2.jpg"));
        listingRepository.save(listing);
        em.flush();

        List<Integer> orders = jdbc.queryForList(
                "select sort_order from wholesale.listing_image where listing_id = ? order by url",
                Integer.class, listing.getId());
        assertThat(orders).containsExactly(0, 1);
    }

    @Test
    void listing_variant는_복합키로_저장되고_가격을_갱신한다() {
        Listing listing = 게시글을_만든다("가격");
        listingRepository.save(listing);
        em.flush();

        ListingVariant price = ListingVariant.builder()
                .listingId(listing.getId()).variantId(variant.getId())
                .salePrice(29000).orderLimit(0)
                .build();
        listingVariantRepository.save(price);
        em.flush();
        em.clear();

        ListingVariant found = listingVariantRepository
                .findById(new ListingVariantId(listing.getId(), variant.getId())).orElseThrow();
        assertThat(found.getSalePrice()).isEqualTo(29000);

        found.reprice(31000, 50);
        em.flush();

        Integer raw = jdbc.queryForObject(
                "select sale_price from wholesale.listing_variant where listing_id = ? and variant_id = ?",
                Integer.class, listing.getId(), variant.getId());
        assertThat(raw).isEqualTo(31000);
    }

    private Listing 게시글을_만든다(String title) {
        return Listing.builder()
                .product(product)
                .title(title)
                .description("넉넉한 오버핏")
                .singlePieceAllowed(true)
                .build();
    }
}
