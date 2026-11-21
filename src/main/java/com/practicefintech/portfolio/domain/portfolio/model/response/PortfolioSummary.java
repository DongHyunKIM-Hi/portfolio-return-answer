package com.practicefintech.portfolio.domain.portfolio.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 포트폴리오 상세 응답의 {@code summary} 부분. 금액 세 개와 수익률, 그리고 쓴 시세의 정보를 담는다.
 *
 * <p>{@code priceStale}은 명세에 없는 추가 필드다 (가정 G1).
 * 시세 서버 장애로 마지막으로 받은 시세를 계속 쓰고 있으면 true가 되어, 값이 오래되었을 수 있음을 알린다.
 * {@code @JsonInclude(NON_NULL)}을 붙였으므로 null인 필드는 JSON에 아예 나타나지 않는다.
 * 정상일 때 응답을 명세와 똑같이 유지하고, 장애일 때만 필드가 하나 늘어나게 하려고 이렇게 했다.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortfolioSummary {

    /** 매입금액 합계. 담은 시점의 가격으로 계산하므로 시세가 움직여도 바뀌지 않는다. */
    private BigDecimal totalCost;

    /** 평가금액 합계. 현재가로 계산한다. */
    private BigDecimal totalValue;

    /** 수익금. 평가금액에서 매입금액을 뺀 값이고 손실이면 음수다. */
    private BigDecimal profit;

    /** 수익률(%). 소수 둘째 자리까지 반올림한 값이라 10.00처럼 자리수가 고정돼 나간다. */
    private BigDecimal returnRate;

    /** 계산에 쓴 시세의 기준 시각. 시세 서버가 응답에 담아 준 값을 그대로 전달한다. */
    private Instant asOf;

    /** 오래된 시세로 계산했는지. 정상일 때는 null이라 응답에서 빠진다. */
    private Boolean priceStale;
}
