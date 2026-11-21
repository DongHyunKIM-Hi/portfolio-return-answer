package com.practicefintech.portfolio.domain.portfolio.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요청 본문의 보유 종목 한 줄. 1단계에서는 받기만 하고 검사하지 않는다.
 *
 * <p>검증 애너테이션이 하나도 없어서 수량 0이나 빈 코드도 그대로 통과한다.
 * 2단계에서 {@code @NotBlank}와 수량 범위({@code @Min(1)}, {@code @Max(1_000_000)})가 붙는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRequest {

    private String code;

    /**
     * 수량. {@code long} 대신 {@code Long}으로 받으므로, 값이 아예 빠지면 0이 아니라 null이 들어온다.
     * 1단계에서는 그 null을 걸러내지 않아 저장하는 쪽에서 터진다.
     */
    private Long quantity;
}
