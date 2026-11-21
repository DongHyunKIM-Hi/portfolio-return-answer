package com.practicefintech.portfolio.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 에러 응답 공통 형식. 명세가 {@code code}와 {@code message} 두 필드로 고정해 두었다.
 * 모든 에러가 이 클래스 하나를 거쳐 나가므로, 검증 실패든 못 찾은 포트폴리오든 본문 모양이 같다.
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {

    /** 기계가 보는 값. INVALID_REQUEST처럼 명세에 적힌 문자열이라 마음대로 바꿀 수 없다. */
    private String code;

    /** 사람이 읽는 설명. 어떤 값이 문제였는지 담고, 내부 예외 메시지나 스택을 그대로 노출하지 않는다. */
    private String message;
}
