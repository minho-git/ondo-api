package com.ondo.wholesale.product.dto.response;

/** 상품 상세의 색상. 팔레트({@code GET /colors})의 색상 id 와 같은 값이다. */
public record ColorResponse(Long id, String name, String hex, String groupName) {}
