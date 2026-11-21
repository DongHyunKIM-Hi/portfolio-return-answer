package com.practicefintech.portfolio.domain.portfolio.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 포트폴리오 생성과 수정 요청 본문. 1단계에서는 JSON을 객체로 받는 일만 한다.
 *
 * <p>검증이 없으므로 이름이 빈 문자열이어도, 보유 종목이 빈 배열이어도 201로 저장된다.
 * 2단계에서 이름 길이(1자 이상 50자 이하)와 종목 개수(1개 이상 20개 이하) 제한,
 * 그리고 원소 하나하나를 검사하게 하는 {@code @Valid}가 붙는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequest {

    private String name;

    private List<HoldingRequest> holdings;
}
