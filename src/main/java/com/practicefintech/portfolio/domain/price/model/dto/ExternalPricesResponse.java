package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 가상 시세 서버의 {@code GET /v1/prices} 응답을 받는 형식.
 *
 * <p>외부 서버가 주는 모양과 우리 API가 내보내는 모양을 섞지 않으려고 {@code External} 접두어를 붙여 따로 두었다.
 * 외부 응답 형식이 바뀌어도 고칠 자리가 이 패키지 안에서 끝난다.
 *
 * <p>Jackson이 JSON을 이 객체로 만들 때 기본 생성자로 만든 뒤 필드를 채우므로
 * {@code @NoArgsConstructor}가 필요하고, {@code @Setter} 없이도 값이 들어간다.
 */
@Getter
@NoArgsConstructor
public class ExternalPricesResponse {

    /** 시세 서버가 알려주는 기준 시각. 우리가 받은 시각이 아니라 이 값을 그대로 응답의 {@code asOf}로 쓴다. */
    private Instant asOf;

    /** 상품별 시세. 배열로 오므로 받는 쪽에서 코드별 Map으로 바꿔 쓴다. */
    private List<ExternalPriceQuote> prices;
}
