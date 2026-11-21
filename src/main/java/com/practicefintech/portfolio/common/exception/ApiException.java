package com.practicefintech.portfolio.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 명세에 적힌 에러 하나를 그대로 표현하는 예외. 상태 코드와 에러 코드를 함께 들고 다닌다.
 *
 * <p>1단계에서는 이 예외를 던지는 코드가 아직 없다. 에러 응답을 다루지 않아서, 잘못된 요청은
 * 검증 없이 통과하거나 계산 도중 터져 500으로 나간다.
 *
 * <p>2단계에서 서비스가 이 예외를 던지고, 새로 만드는 {@code GlobalExceptionHandler}가 응답으로 바꾼다.
 * 그 구조라야 서비스에 {@code ResponseEntity}가 등장하지 않고, 같은 에러가 여러 군데서 다른 형식으로 나가지 않는다.
 *
 * <p>{@code RuntimeException}을 고른 이유는 검사 예외로 두면 메서드마다 throws 선언이 번져 나가고,
 * 호출하는 쪽이 할 수 있는 일도 없기 때문이다.
 */
@Getter
public class ApiException extends RuntimeException {

    /** HTTP 상태 코드. 400, 404, 503처럼 명세가 정한 값을 그대로 넣는다. */
    private final HttpStatus status;

    /** 응답 본문의 {@code code} 값. INVALID_REQUEST, UNKNOWN_PRODUCT 등 명세에 적힌 문자열이다. */
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        // message는 RuntimeException이 들고 있다가 응답 본문의 message 자리로 나간다.
        super(message);
        this.status = status;
        this.code = code;
    }
}
