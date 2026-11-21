package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 토큰 발급 응답. */
@Getter
@NoArgsConstructor
public class ExternalTokenResponse {

    /** 시세 조회의 Authorization 헤더에 Bearer 뒤에 붙이는 값. */
    private String accessToken;

    /** 토큰 종류. 항상 Bearer라 쓰지 않지만, 응답에 무엇이 오는지 보이게 두었다. */
    private String tokenType;

    /**
     * 토큰의 남은 수명(초). 가상 시세 서버는 120을 준다.
     * 만료 시각을 서버 응답으로 계산하므로, 수명이 바뀌어도 코드를 고칠 필요가 없다.
     */
    private long expiresIn;
}
