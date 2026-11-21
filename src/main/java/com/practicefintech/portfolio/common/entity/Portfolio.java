package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 만든 포트폴리오. 보유 종목을 여럿 들고 있고, 수정 API는 그 목록을 전체 교체한다.
 *
 * <p>기본 생성자를 {@code protected}로 막아 둔 이유는 JPA가 객체를 만들 때만 쓰게 하려는 것이다.
 * 이름 없는 포트폴리오가 코드에서 만들어지는 것을 막고, 만들 때 이름을 반드시 받게 한다.
 */
@Entity
@Getter
@Table(name = "portfolios")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 포트폴리오 이름. 수정 API로 바꿀 수 있어 여기만 {@code @Setter}를 열어 두었다. */
    @Setter
    private String name;

    /**
     * 보유 종목. 설정 세 개가 각각 필요한 이유가 있다.
     * <ul>
     *   <li>{@code cascade = ALL}: 포트폴리오를 저장하거나 지우면 종목도 함께 처리된다.</li>
     *   <li>{@code orphanRemoval = true}: 목록에서 빠진 종목을 DB에서도 지운다.
     *       이것이 없으면 수정으로 뺀 종목이 포트폴리오 참조만 끊긴 채 테이블에 남는다.</li>
     *   <li>{@code fetch = EAGER}: 조회할 때마다 종목이 필요하다. 상세와 목록 둘 다 종목을 합산하므로
     *       지연 로딩으로 두면 트랜잭션 밖에서 꺼내다 실패하거나 조회 쿼리가 포트폴리오 수만큼 더 나간다.</li>
     * </ul>
     * 필드를 {@code final}로 두어 리스트 자체를 다른 리스트로 갈아 끼우지 못하게 했다.
     * JPA가 관리하는 컬렉션을 통째로 교체하면 변경 추적이 끊어진다.
     */
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private final List<Holding> holdings = new ArrayList<>();

    public Portfolio(String name) {
        this.name = name;
    }

    /**
     * 보유 종목을 새 목록으로 바꾼다. 생성과 수정이 모두 이 메서드를 쓴다.
     *
     * <p>리스트를 교체하지 않고 {@code clear} 뒤 다시 담는 것이 요점이다.
     * 그래야 위의 {@code orphanRemoval}이 빠진 종목을 지워 준다.
     * {@code assignTo}로 양쪽 참조를 맞춰 주지 않으면 외래 키가 비어 저장된다.
     */
    public void replaceHoldings(List<Holding> newHoldings) {
        holdings.clear();
        for (Holding h : newHoldings) {
            h.assignTo(this);
            holdings.add(h);
        }
    }
}
