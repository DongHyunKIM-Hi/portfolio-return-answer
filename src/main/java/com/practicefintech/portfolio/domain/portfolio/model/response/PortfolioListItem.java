package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 포트폴리오 목록의 한 항목. 명세가 네 필드만 요구하므로 보유 종목 내역은 넣지 않는다.
 * 목록에 내역까지 담으면 포트폴리오가 늘어날수록 응답이 커지고, 상세 조회와 역할이 겹친다.
 */
@Getter
@AllArgsConstructor
public class PortfolioListItem {
    private Long id;
    private String name;
    private BigDecimal totalValue;
    private BigDecimal returnRate;
}
