package com.practicefintech.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 서버 시작점.
 *
 * <p>애너테이션 세 개가 각각 하는 일이 있다.
 * <ul>
 *   <li>{@code @SpringBootApplication}: 이 패키지 아래를 훑어 컴포넌트를 찾고 자동 설정을 켠다.</li>
 *   <li>{@code @EnableScheduling}: {@code @Scheduled}가 붙은 메서드를 주기 실행한다.
 *       이것이 없으면 주기 갱신 코드를 만들어도 애너테이션이 무시되어 한 번도 실행되지 않는다.</li>
 *   <li>{@code @ConfigurationPropertiesScan}: {@code @ConfigurationProperties} 클래스를 빈으로 등록한다.
 *       설정 클래스마다 {@code @EnableConfigurationProperties}를 붙이지 않아도 된다.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PortfolioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
