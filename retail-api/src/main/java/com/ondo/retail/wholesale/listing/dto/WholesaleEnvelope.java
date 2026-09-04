package com.ondo.retail.wholesale.listing.dto;

/**
 * 도매 응답의 봉투 (MUL-88). 도매는 성공 응답을 전부 {@code { "data": ... }} 로 감싼다.
 *
 * <p>페이징 있는 목록에만 {@code meta} 가 붙고 나머지는 null 이다.
 *
 * @param <T> 봉투 안의 실제 내용
 */
public record WholesaleEnvelope<T>(T data, PageMeta meta) {

    /** 도매의 페이지 정보. page 는 0-base 다. 소매 봉투의 hasNext 는 여기 없어서 계산한다. */
    public record PageMeta(int page, int size, long totalElements, int totalPages) {}
}
