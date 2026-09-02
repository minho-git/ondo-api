package com.ondo.wholesale.settlement;

import com.ondo.wholesale.settlement.dto.BankAccountCreateRequest;
import com.ondo.wholesale.settlement.dto.BankAccountResponse;
import com.ondo.wholesale.settlement.dto.BankAccountUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정산 계좌 계약 스텁 (MUL-83) — 원본: api-lite/07_정산 계좌 4종.
 * example 응답만 반환하며 실구현이 서비스 계층으로 교체한다.
 */
@Tag(name = "07 정산")
@RestController
@RequestMapping("/api/wholesale/bank-accounts")
public class BankAccountController {

    @Operation(summary = "정산 계좌 목록", description = """
            페이징 없음(계좌는 소수). 정렬은 주계좌 먼저 → 등록순 고정(sort 미지원).
            주계좌는 최대 1개, 계좌 0건도 정상 상태.""")
    @GetMapping
    public List<BankAccountResponse> accounts() {
        return BankAccountStubExamples.accounts();
    }

    @Operation(summary = "정산 계좌 등록", description = """
            첫 계좌는 `isPrimary` 값과 무관하게 주계좌가 된다. `isPrimary: true`면 기존 주계좌가
            한 트랜잭션에서 내려간다 — 응답에는 새 계좌만 담기므로 목록을 재조회한다.
            계좌번호 형식은 검증하지 않는다(은행마다 달라 자유 입력).

            에러: 400 `VALIDATION_FAILED` · `DUPLICATE_BANK_ACCOUNT`(data 에 기존 계좌 id)""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse create(@RequestBody BankAccountCreateRequest request) {
        return BankAccountStubExamples.primaryAccount();
    }

    @Operation(summary = "정산 계좌 수정", description = """
            보낸 필드만 바뀐다(공통 §6). `memo`만 null 로 지울 수 있다.
            주계좌를 `isPrimary: false`로 직접 해제할 수 없다 — 다른 계좌를 승격하면 부수 효과로 내려간다.

            에러: 400 `VALIDATION_FAILED` · `PRIMARY_ACCOUNT_CANNOT_BE_UNSET` ·
            `DUPLICATE_BANK_ACCOUNT` / 404 `RESOURCE_NOT_FOUND`""")
    @PatchMapping("/{bankAccountId}")
    public BankAccountResponse update(@PathVariable Long bankAccountId,
                                      @RequestBody BankAccountUpdateRequest request) {
        return BankAccountStubExamples.primaryAccount();
    }

    @Operation(summary = "정산 계좌 삭제", description = """
            완전 삭제(복구 없음) — 입금 이력이 계좌를 참조하지 않아 남길 이유가 없다.
            주계좌를 지우면 가장 먼저 등록된 계좌가 조용히 승격되므로 목록을 재조회한다.

            에러: 404 `RESOURCE_NOT_FOUND`(이미 삭제 포함)""")
    @DeleteMapping("/{bankAccountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bankAccountId) {
    }
}
