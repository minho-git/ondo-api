package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.SignupResponse;
import com.ondo.wholesale.wholesaler.ApprovalRequest;
import com.ondo.wholesale.wholesaler.ApprovalRequestRepository;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 4종을 한 트랜잭션에 넣는다 — 도매처 · 동의 · 서류 · 심사신청.
 *
 * <p><b>왜 {@link SignupService} 와 클래스를 나눴나.</b> 두 가지 이유가 겹친다.
 *
 * <ol>
 *   <li>BCrypt 해싱을 트랜잭션 밖에 두려고. 해싱은 60~250ms 동안 CPU 를 도는데,
 *       트랜잭션 안에서 하면 그동안 DB 커넥션을 잡고 있는다. HikariCP 기본 풀이
 *       10 이라 동시 가입이 몰리면 풀이 마른다.</li>
 *   <li>UNIQUE 위반 예외를 잡으려고. JPA 는 INSERT 를 커밋 시점에 몰아서 내보내므로
 *       예외도 그때 난다. 같은 클래스 안에서 private 메서드를 부르면 스프링 프록시를
 *       타지 않아 {@code @Transactional} 이 아예 안 걸린다(자기호출 문제).
 *       빈을 나눠야 커밋이 호출부 밖에서 끝나고 그 예외를 잡을 수 있다.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class WholesalerRegistrar {

    private final WholesalerRepository wholesalerRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    /**
     * 도매처와 그에 딸린 동의 · 서류를 저장하고, 심사 대기 라운드를 연다.
     * 하나라도 실패하면 전부 롤백된다.
     */
    @Transactional
    public SignupResponse register(Wholesaler wholesaler) {
        // 동의와 서류는 cascade = PERSIST 로 함께 들어간다
        Wholesaler saved = wholesalerRepository.save(wholesaler);

        // 심사신청은 도매처에 딸린 게 아니라 재신청 때 혼자 늘어나는 이력이라 따로 저장한다
        ApprovalRequest request = approvalRequestRepository.save(
                ApprovalRequest.pending(saved.getId()));

        return new SignupResponse(
                saved.getApprovalStatus(),
                saved.getBizName(),
                saved.getBizRegNo(),
                request.getCreatedAt());   // 화면의 "신청 일시"
    }
}
