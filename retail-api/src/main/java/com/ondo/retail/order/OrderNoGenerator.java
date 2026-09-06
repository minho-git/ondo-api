package com.ondo.retail.order;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 화면에 보여주는 주문번호를 만든다 (MUL-98).
 *
 * <pre>
 *   20260902-1420-0088
 *   날짜      시각  그날의 연번
 * </pre>
 *
 * <p>도매 장끼(D-076)와 같은 방식이다 — 날짜를 같이 들고 있다가 날이 바뀌면 1 부터
 * 다시 센다. 다만 채번 축이 소매처가 아니라 <b>날짜</b>라서, 도매처럼 계정 행에
 * 컬럼을 붙이지 않고 표를 따로 뒀다({@code retail.order_no_seq}).
 *
 * <p>소매처별로 세지 않는 건 {@code order_group_no_uk} 가 번호 전체에 걸려 있어서다.
 * 소매처마다 1 부터 세면 두 소매처가 같은 분에 첫 주문을 넣을 때 번호가 통째로 겹친다.
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    /** 영업이 한국 기준이라 날짜도 한국 시각으로 끊는다. 새벽 영업이라 UTC 로 끊으면 하루가 갈린다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter PREFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final JdbcClient jdbc;

    /**
     * @param orderedAt 주문 시각. 번호의 앞부분이 이 값에서 나온다 —
     *                  따로 {@code now()} 를 부르면 번호와 주문 시각이 어긋난다
     */
    public String next(OffsetDateTime orderedAt) {
        LocalDate date = orderedAt.atZoneSameInstant(KST).toLocalDate();
        return "%s-%04d".formatted(orderedAt.atZoneSameInstant(KST).format(PREFIX), nextSeq(date));
    }

    /**
     * 그날의 다음 연번.
     *
     * <p>읽고 더해서 쓰지 않는다. 한 문장이라 두 주문이 동시에 들어와도 번호가 안 겹친다 —
     * 도매 주문번호 채번과 같은 이유다(숙제 9번). 그날 첫 주문이면 행이 없으므로
     * {@code INSERT} 로 시작하고, 이미 있으면 {@code ON CONFLICT} 가 올린다.
     */
    private int nextSeq(LocalDate date) {
        return jdbc.sql("""
                        INSERT INTO retail.order_no_seq (order_date, last_seq)
                        VALUES (:date, 1)
                        ON CONFLICT (order_date)
                        DO UPDATE SET last_seq = retail.order_no_seq.last_seq + 1,
                                      updated_at = now()
                        RETURNING last_seq
                        """)
                .param("date", date)
                .query(Integer.class)
                .single();
    }
}
