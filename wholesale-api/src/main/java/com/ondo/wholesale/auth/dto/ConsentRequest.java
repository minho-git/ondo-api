package com.ondo.wholesale.auth.dto;

import com.ondo.wholesale.wholesaler.ConsentType;
import jakarta.validation.constraints.NotNull;

/**
 * 동의 한 건. 6종을 전부 받는다.
 *
 * <p>{@code agreed} 를 원시 타입 {@code boolean} 이 아니라 {@link Boolean} 으로 둔 이유 —
 * 원시 타입이면 값을 안 보냈을 때 조용히 {@code false}(미동의)가 되어버린다.
 * "미동의"와 "안 보냄"은 다르고, 후자는 잘못된 요청이다.
 */
public record ConsentRequest(

        @NotNull(message = "동의 항목을 지정해주세요.")
        ConsentType type,

        @NotNull(message = "동의 여부를 보내주세요.")
        Boolean agreed) {
}
