package com.ondo.wholesale.retailgateway.dto;

import java.util.List;

/**
 * 소매 상품 목록의 검색·필터 조건 (MUL-88).
 *
 * <p>컨트롤러가 받은 쿼리 파라미터를 묶어 아래로 넘긴다. 전부 선택이고, 안 주면 안 거른다.
 *
 * <p>색상·사이즈·가격은 <b>게시글이 아니라 옵션에 걸리는 조건</b>이다. 옵션 하나라도
 * 맞으면 그 게시글이 나온다 — "네이비 M" 을 고르면 네이비 M 이 있는 상품이 나오는 것이지
 * 네이비 M 만 파는 상품이 나오는 게 아니다.
 *
 * @param q          게시 제목 부분일치. 대소문자를 안 가린다
 * @param categoryId 하위 카테고리를 포함한다. depth 1·2 를 주면 그 아래 전부
 */
public record RetailListingSearchCondition(
        String q,
        Long categoryId,
        List<Long> colorIds,
        List<String> sizes,
        Integer priceFrom,
        Integer priceTo
) {}
