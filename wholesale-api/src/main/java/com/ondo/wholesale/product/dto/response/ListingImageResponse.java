package com.ondo.wholesale.product.dto.response;

/** 게시 이미지. {@code sortOrder} ASC 정렬, {@code 0}이 대표. */
public record ListingImageResponse(Long id, String url, int sortOrder) {}
