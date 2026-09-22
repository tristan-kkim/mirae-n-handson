package com.example.item;

/**
 * 문항 상태 코드. DB 컬럼 {@code item.status CHAR(1)} 의 값.
 * 삭제는 플래그가 아니라 상태 코드 {@code D} 로 표현한다 — 외부에 노출하는 문항은 {@link #ACTIVE} 만.
 */
public final class ItemStatus {

    /** 공개. */
    public static final String ACTIVE = "A";
    /** 삭제. */
    public static final String DELETED = "D";
    /** 검수 중. */
    public static final String REVIEWING = "R";

    private ItemStatus() {
    }
}
