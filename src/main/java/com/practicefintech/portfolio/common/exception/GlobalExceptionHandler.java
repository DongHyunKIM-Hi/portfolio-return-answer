package com.practicefintech.portfolio.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외를 명세의 에러 응답 형식({@link ErrorResponse})으로 바꾼다. 2단계에서 새로 만든 클래스다.
 *
 * <p>1단계에는 이 클래스가 없었다. 그래서 없는 상품 코드를 보내면 계산 도중 터진 예외가 그대로 500으로 나가고,
 * 없는 포트폴리오를 조회해도 500이었다. 상태 코드와 본문 형식을 한 군데서 정하는 자리가 여기다.
 *
 * <p>{@code @RestControllerAdvice}라 모든 컨트롤러에서 올라온 예외가 이곳을 거친다.
 * 처리기가 여럿일 때 스프링은 예외 타입이 가장 구체적인 것을 고르므로, 아래 세 메서드의 순서는 동작에 영향이 없다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 직접 던진 예외. 상태 코드와 에러 코드를 예외가 들고 있으므로 그대로 꺼내 쓴다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /**
     * 요청이 잘못된 경우를 모두 400 {@code INVALID_REQUEST}로 모은다. 각 예외가 걸리는 상황은 이렇다.
     * <ul>
     *   <li>{@code MethodArgumentNotValidException}: 본문이 {@code @Valid} 검증을 통과하지 못함</li>
     *   <li>{@code HttpMessageNotReadableException}: JSON 문법이 깨졌거나 타입이 맞지 않음(수량에 문자열 등)</li>
     *   <li>{@code ConstraintViolationException}: 파라미터 수준 검증 위반</li>
     *   <li>{@code MissingServletRequestParameterException}: 필수 파라미터 누락</li>
     *   <li>{@code MethodArgumentTypeMismatchException}: 경로 변수나 파라미터의 타입 변환 실패</li>
     *   <li>{@code IllegalArgumentException}: 잘못된 인자로 올라온 그 밖의 경우</li>
     * </ul>
     * 어느 필드가 틀렸는지는 응답에 넣지 않고 같은 문구로 돌려준다. 명세가 {@code code}와 {@code message} 두 필드만
     * 요구하고, 예외 메시지를 그대로 내보내면 내부 구조가 드러난다.
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."));
    }

    /**
     * 위에서 걸리지 않은 나머지 전부를 받는 그물이다.
     *
     * <p>없는 경로(404)나 지원하지 않는 메서드, 미디어 타입(405, 415)처럼 스프링이 상태 코드를 정해 둔 예외는
     * 그 코드를 그대로 쓰고, 나머지는 500으로 응답한다. 둘 다 명세의 {@code code}, {@code message} 형식을 따른다.
     *
     * <p>이 메서드가 없으면 처리하지 않은 예외가 스프링 기본 오류 본문({@code timestamp}, {@code path} 등)으로
     * 나가서 응답 형식이 두 종류가 된다. 500으로 나가는 경우에도 예외 메시지를 그대로 쓰지 않고 고정 문구를 쓴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        // 스프링의 ErrorResponse를 구현한 예외는 자신이 어떤 상태 코드로 나가야 하는지 알고 있다.
        // 그 값을 살려 주지 않으면 없는 경로 요청이 404가 아니라 500으로 나간다.
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(new ErrorResponse("INVALID_REQUEST", "지원하지 않는 요청입니다."));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
