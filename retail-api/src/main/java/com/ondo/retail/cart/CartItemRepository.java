package com.ondo.retail.cart;

import com.ondo.retail.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByRetailerIdOrderByCreatedAtDesc(Long retailerId);

    Optional<CartItem> findByRetailerIdAndVariantId(Long retailerId, Long variantId);

    /** 헤더 뱃지가 쓴다. 담은 수량이 아니라 종류 수다. */
    long countByRetailerId(Long retailerId);
}
