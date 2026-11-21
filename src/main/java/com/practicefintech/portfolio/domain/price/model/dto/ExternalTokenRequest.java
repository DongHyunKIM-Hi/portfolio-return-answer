package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 토큰 발급 요청 본문({@code POST /auth/token}).
 * 자격 정보는 설정에서 읽어 넣으며, 코드에 적어 두지 않는다.
 */
@Getter
@AllArgsConstructor
public class ExternalTokenRequest {
    private String clientId;
    private String clientSecret;
}
