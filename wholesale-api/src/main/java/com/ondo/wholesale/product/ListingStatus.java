package com.ondo.wholesale.product;

/** 게시글 상태. 전이는 시즌 종료·재개 전용 경로로만 한다 (PATCH 로 못 바꾼다). */
public enum ListingStatus {
    ON_SALE, SEASON_ENDED
}
