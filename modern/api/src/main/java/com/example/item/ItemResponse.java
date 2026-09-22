package com.example.item;

import java.util.List;

/**
 * 문항 응답. {@code unit} 은 단원 코드({@code M5-1}), {@code tags} 는 태그 이름 목록(id 순).
 */
public record ItemResponse(
    Integer id,
    String title,
    String stem,
    String unit,
    Integer level,
    String status,
    List<String> tags) {

    static ItemResponse from(Item item) {
        return new ItemResponse(
            item.getId(),
            item.getTitle(),
            item.getStem(),
            item.getUnit().getCode(),
            item.getLevel(),
            item.getStatus(),
            item.getTags().stream().map(Tag::getName).toList());
    }
}
