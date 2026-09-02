package com.ondo.wholesale.settlement;

import com.ondo.wholesale.settlement.dto.BankAccountResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 계좌 스텁 example — api-lite/07_정산 계좌 문서의 예시 그대로. */
final class BankAccountStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private BankAccountStubExamples() {
    }

    static BankAccountResponse primaryAccount() {
        return new BankAccountResponse(91L, "신한은행", "110-482-948102", "서울유통",
                "주거래 계좌", true, OffsetDateTime.of(2025, 1, 15, 10, 0, 0, 0, KST));
    }

    static List<BankAccountResponse> accounts() {
        return List.of(
                primaryAccount(),
                new BankAccountResponse(92L, "국민은행", "829102-01-294812", "김서울",
                        null, false, OffsetDateTime.of(2025, 2, 10, 9, 20, 0, 0, KST)),
                new BankAccountResponse(93L, "기업은행", "032-094812-01-011", "서울유통",
                        null, false, OffsetDateTime.of(2025, 4, 2, 14, 5, 0, 0, KST)));
    }
}
