package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.ListingStatus;
import com.ondo.wholesale.product.dto.response.ListingResponse;
import com.ondo.wholesale.product.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 시즌 전이 (MUL-94). 상태는 ON_SALE ↔ SEASON_ENDED 둘뿐이고,
 * 시즌 종료는 마켓 노출만 내린다 — 상품·재고·판매가는 전부 남아 재개로 되살아난다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ListingSeasonService {

    private final ListingRepository listingRepository;
    private final ProductDetailAssembler productDetailAssembler;

    public ListingResponse endSeason(Long wholesalerId, Long listingId) {
        Listing listing = owned(wholesalerId, listingId);
        if (listing.getStatus() == ListingStatus.SEASON_ENDED) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        listing.endSeason();
        return productDetailAssembler.toListingResponse(listing);
    }

    public ListingResponse reopen(Long wholesalerId, Long listingId) {
        Listing listing = owned(wholesalerId, listingId);
        if (listing.getStatus() == ListingStatus.ON_SALE) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        listing.reopen();
        return productDetailAssembler.toListingResponse(listing);
    }

    private Listing owned(Long wholesalerId, Long listingId) {
        return listingRepository.findOwnedById(listingId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("게시글이 없거나 접근할 수 없습니다."));
    }
}
