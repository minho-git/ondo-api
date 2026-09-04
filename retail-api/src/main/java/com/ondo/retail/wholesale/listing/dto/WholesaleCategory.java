package com.ondo.retail.wholesale.listing.dto;

import java.util.List;

/** 도매가 주는 카테고리 노드 (MUL-88). 3단 중첩이다. */
public record WholesaleCategory(Long id, String name, List<WholesaleCategory> children) {}
