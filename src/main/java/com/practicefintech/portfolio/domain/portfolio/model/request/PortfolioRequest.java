package com.practicefintech.portfolio.domain.portfolio.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 포트폴리오 생성과 수정 요청 본문. 두 API가 같은 형식을 쓴다.
 *
 * <p>검증 규칙을 요청 클래스에 모아 두면, 서비스는 통과한 값만 받게 되어 계산 코드에 조건문이 섞이지 않는다.
 * 위반하면 {@code MethodArgumentNotValidException}이 올라가고, 예외 처리기가 400 {@code INVALID_REQUEST}로 바꾼다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequest {

    /** 이름은 1자 이상 50자 이하다. {@code @NotBlank}가 공백만 있는 이름까지 막는다. */
    @NotBlank
    @Size(min = 1, max = 50)
    private String name;

    /**
     * 보유 종목 1개 이상 20개 이하.
     *
     * <p>{@code @NotEmpty}는 목록이 null이거나 빈 경우를, {@code @Size}는 개수 범위를 본다.
     * 원소에 붙인 {@code @Valid}는 빠뜨리기 쉬운 자리다. 이것이 없으면 {@code HoldingRequest}의 애너테이션이
     * 검사되지 않아 수량 0이나 빈 코드가 그대로 통과한다.
     */
    @NotEmpty
    @Size(min = 1, max = 20)
    private List<@Valid HoldingRequest> holdings;
}
