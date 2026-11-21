package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상세 응답의 보유 종목 한 줄. 엔티티 {@code Holding}을 그대로 내보내지 않고 이 클래스로 옮겨 담는다.
 * 엔티티에는 내부 id와 포트폴리오 참조가 있어 그대로 직렬화하면 응답에 없어야 할 값이 섞이고,
 * 지연 로딩된 연관 관계를 직렬화하다 터지기도 한다.
 *
 * <p>매입가와 현재가를 나란히 두는 것이 이 응답의 요점이다. 두 값이 다르다는 사실이 수익의 근거가 된다.
 */
@Getter
@AllArgsConstructor
public class HoldingView {

    private String code;
    private long quantity;

    /** 담은 시점의 가격. 저장된 값을 그대로 내보낸다. */
    private BigDecimal purchasePrice;

    /** 조회 시점의 가격. 받아 둔 시세에서 읽는다. */
    private BigDecimal currentPrice;
}
