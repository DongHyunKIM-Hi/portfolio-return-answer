package com.practicefintech.portfolio.domain.portfolio.repository;

import com.practicefintech.portfolio.common.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 포트폴리오 저장소. 쓰는 기능이 {@code save}, {@code findById}, {@code findAll}, {@code delete}뿐이라
 * {@link JpaRepository}가 주는 것으로 충분하고, 직접 선언한 메서드가 없다.
 *
 * <p>목록 정렬은 쿼리 메서드 이름으로 고정하지 않고 호출할 때 {@code Sort}를 넘기는 방식을 썼다.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
