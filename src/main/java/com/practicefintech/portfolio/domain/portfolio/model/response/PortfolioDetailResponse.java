package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 포트폴리오 상세 응답. 생성(201), 조회(200), 수정(200)이 모두 이 형식으로 응답한다.
 * 세 API가 같은 형식을 쓰므로, 만든 직후와 조회했을 때 보이는 값의 모양이 달라지지 않는다.
 */
@Getter
@AllArgsConstructor
public class PortfolioDetailResponse {
    private Long id;
    private String name;

    /** 보유 종목. 요청에 적힌 순서를 그대로 유지한다. */
    private List<HoldingView> holdings;

    /** 금액과 수익률 묶음. 종목별 합계를 여기 한 번에 담는다. */
    private PortfolioSummary summary;
}
