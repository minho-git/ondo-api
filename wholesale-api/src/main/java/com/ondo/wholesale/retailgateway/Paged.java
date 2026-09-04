package com.ondo.wholesale.retailgateway;

import java.util.List;

/**
 * 한 장과 전체 개수. 컨트롤러가 {@code meta} 를 만드는 데 쓴다.
 *
 * <p>스프링의 {@code Page} 를 안 쓴 건 그게 JSON 으로 나갈 물건이 아니어서다 —
 * 여기서는 컨트롤러에 넘기는 중간 모양일 뿐이다.
 *
 * <p>상품(MUL-88)이 쓰던 걸 미송(MUL-97)이 같이 쓰게 되면서 밖으로 뺐다.
 *
 * @param totalElements 조건에 맞는 전체 개수. 빈 장이라도 채워서 내린다 —
 *                      소매가 "마지막 장 다음" 인지 "결과 없음" 인지 알아야 한다
 */
public record Paged<T>(List<T> content, long totalElements) {}
