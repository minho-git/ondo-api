package com.ondo.retail.wholesale.dto;

/**
 * 도매 응답의 봉투 (MUL-88). 도매는 성공 응답을 전부 {@code { "data": ... }} 로 감싼다.
 *
 * <p>페이징 있는 목록에만 {@code meta} 가 붙고 나머지는 null 이다.
 *
 * <p>상품이 쓰던 걸 미송(MUL-97)이 같이 쓰게 되면서 {@code listing} 밖으로 뺐다.
 * 봉투는 어느 API 냐와 상관없는 도매 공통 규약이다.
 *
 * @param <T> 봉투 안의 실제 내용
 */
public record WholesaleEnvelope<T>(T data, PageMeta meta) {

    /** 도매의 페이지 정보. page 는 0-base 다. 소매 봉투의 hasNext 는 여기 없어서 계산한다. */
    public record PageMeta(int page, int size, long totalElements, int totalPages) {}
}
