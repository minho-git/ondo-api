package com.ondo.wholesale.wholesaler;

import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 도메인 엔티티가 실제 스키마에 맞게 매핑됐는지 검증 (MUL-68).
 *
 * <p>가입은 도매처 1 + 동의 6 + 서류 2~3 + 신청 1 을 한 번에 저장한다. 그중 동의와 서류는
 * 도매처에 딸린 것이라 <b>도매처를 저장하면 같이 저장돼야</b> 한다. 서비스가 자식을
 * 하나하나 저장하게 두면 나중에 한 줄 빠뜨렸을 때 조용히 누락된다.
 */
@SpringBootTest
@Transactional
class WholesalerMappingTest extends PostgresTestSupport {

    @Autowired
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 도매처를_저장하면_동의_6종이_함께_저장된다() {
        Wholesaler wholesaler = 도매처를_만든다("consent@ondo.test", "1000000001");
        for (ConsentType type : ConsentType.values()) {
            wholesaler.addConsent(type, type.isRequired());
        }

        Wholesaler saved = 저장하고_다시_읽는다(wholesaler);

        assertThat(saved.getConsents()).hasSize(6);
        assertThat(saved.getConsents()).extracting(WholesalerConsent::getType)
                .containsExactlyInAnyOrder(ConsentType.values());
        // 미동의도 false 행으로 남는다 — "안 물어봤다"와 구분해야 한다
        assertThat(saved.getConsents())
                .filteredOn(c -> !c.isAgreed())
                .hasSize(3);
    }

    @Test
    void 도매처를_저장하면_서류가_함께_저장된다() {
        Wholesaler wholesaler = 도매처를_만든다("doc@ondo.test", "1000000002");
        wholesaler.addDocument(DocumentType.BIZ_REG, "uploads/2026/09/ab12cd34.jpg");
        wholesaler.addDocument(DocumentType.CEO_ID, "uploads/2026/09/ef56gh78.jpg");

        Wholesaler saved = 저장하고_다시_읽는다(wholesaler);

        assertThat(saved.getDocuments()).hasSize(2);

        // 절대 URL 이 아니라 저장소 키가 file_key 컬럼에 들어간다
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select file_key from wholesale.wholesaler_document where wholesaler_id = ?",
                saved.getId());
        assertThat(rows).extracting(r -> r.get("file_key"))
                .containsExactlyInAnyOrder(
                        "uploads/2026/09/ab12cd34.jpg", "uploads/2026/09/ef56gh78.jpg");
    }

    @Test
    void 저장한_값이_그대로_읽힌다() {
        Wholesaler saved = 저장하고_다시_읽는다(도매처를_만든다("plain@ondo.test", "1000000003"));

        assertThat(saved.getEmail()).isEqualTo("plain@ondo.test");
        assertThat(saved.getBizRegNo()).isEqualTo("1000000003");
        assertThat(saved.getBizName()).isEqualTo("온도상사");
        assertThat(saved.getStoreBuilding()).isEqualTo("누죤");
        // 가입 직후는 언제나 심사 대기다
        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 심사_신청은_독립_리포지토리로_저장된다() {
        Wholesaler saved = 저장하고_다시_읽는다(도매처를_만든다("round@ondo.test", "1000000004"));

        ApprovalRequest request = approvalRequestRepository.save(ApprovalRequest.pending(saved.getId()));
        em.flush();

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        // 화면의 "신청 일시" 가 이 값이다
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getWholesalerId()).isEqualTo(saved.getId());
    }

    private Wholesaler 도매처를_만든다(String email, String bizRegNo) {
        return Wholesaler.builder()
                .email(email)
                .passwordHash("$2a$10$hash")
                .phone("01012345678")
                .bizRegNo(bizRegNo)
                .bizName("온도상사")
                .bizOwnerName("김대표")
                .storePhone("0212345678")
                .storeBuilding("누죤")
                .storeUnit("3층 C-25")
                .bizCategory("도매 및 소매업")
                .build();
    }

    /** flush 로 진짜 INSERT 를 내보내고, clear 로 1차 캐시를 비워 DB 에서 다시 읽는다. */
    private Wholesaler 저장하고_다시_읽는다(Wholesaler wholesaler) {
        wholesalerRepository.save(wholesaler);
        em.flush();
        Long id = wholesaler.getId();
        em.clear();
        return wholesalerRepository.findById(id).orElseThrow();
    }
}
