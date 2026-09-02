package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 미송 API 의 가짜 응답. <b>껍데기다 — 로직이 없다.</b>
 *
 * <p>미송은 도매가 만들고 도매가 푼다. 소매는 읽기만 한다. 그래서 도매 쪽 내부 API 가
 * 생기기 전까지는 이 목이 대신한다. 클래스 이름의 Mock 은 그때 검색으로 찾으려는 것이다.
 *
 * <p>예상 입고일이 있는 줄과 <b>없는 줄</b>을 섞어뒀다 — 없을 때 화면에
 * "도매처가 입고일을 안내할 예정이에요" 를 그려야 해서 프론트가 그 분기를 봐야 한다.
 */
@Component
public class MockBackorderData {

    private static final BackorderResponse.Wholesaler MOODON =
            new BackorderResponse.Wholesaler(3L, "무드온");
    private static final BackorderResponse.Wholesaler COTTONCLUB =
            new BackorderResponse.Wholesaler(11L, "코튼클럽");

    /** 오래된 순이다. 명세의 정렬 기준이 {@code orderedAt} 이라 목도 그 순서로 둔다. */
    public List<BackorderResponse> backorders() {
        OffsetDateTime now = OffsetDateTime.now();
        return List.of(
                new BackorderResponse(4402L, 5001L, "20260830-0930-0085", now.minusDays(3),
                        COTTONCLUB, 4380L, "베이직 라운드 니트", "오트밀", "FREE", 4,
                        LocalDate.now().plusDays(1), "공장 재입고 예정"),
                new BackorderResponse(4408L, 5008L, "20260901-1110-0087", now.minusDays(1),
                        MOODON, 4402L, "와이드 데님 팬츠", "네이비", "M", 1,
                        null, null),                                    // 아직 안내 못 받은 줄
                new BackorderResponse(4411L, 5012L, "20260902-1420-0088", now,
                        MOODON, 4410L, "빈티지 플라워 셔츠", "체리레드", "S", 2,
                        LocalDate.now().plusDays(2), "공장 재입고 예정"));
    }
}
