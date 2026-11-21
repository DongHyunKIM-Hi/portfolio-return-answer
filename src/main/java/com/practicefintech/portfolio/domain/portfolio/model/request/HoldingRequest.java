package com.practicefintech.portfolio.domain.portfolio.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요청 본문의 보유 종목 한 줄. 명세가 정한 범위를 애너테이션으로 적어 둔다.
 *
 * <p>여기서 걸러지지 않는 것이 둘 있다. 있는 상품 코드인지와, 같은 코드가 두 번 들어왔는지다.
 * 애너테이션만으로 판단할 수 없어 {@code PortfolioService}에서 확인한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRequest {

    /** 상품 코드. {@code @NotBlank}라 빈 문자열과 공백만 있는 값도 거절한다. */
    @NotBlank
    private String code;

    /**
     * 수량. 1 이상 1,000,000 이하다.
     *
     * <p>{@code long} 대신 {@code Long}으로 받는 이유는 값이 아예 빠진 경우를 구분하기 위해서다.
     * 기본형으로 받으면 누락과 0이 모두 0이 되어 {@code @NotNull}이 걸리지 않는다.
     */
    @NotNull
    @Min(1)
    @Max(1_000_000)
    private Long quantity;
}
