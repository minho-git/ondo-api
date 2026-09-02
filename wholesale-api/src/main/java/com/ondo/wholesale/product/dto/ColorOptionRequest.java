package com.ondo.wholesale.product.dto;

import com.ondo.wholesale.product.Size;

import java.util.List;

/** 색상 하나의 옵션 구성. {@code sizes}는 1개 이상, 색상 내 중복 불가. */
public record ColorOptionRequest(Long colorId, List<Size> sizes) {}
