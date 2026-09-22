package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.item.ItemController;
import com.example.item.ItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** 전체 컨텍스트가 H2(test 프로필)로 뜨는지 — 엔티티 매핑 · DDL 오류를 여기서 잡는다. */
@SpringBootTest
@ActiveProfiles("test")
class ItemBankApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("test 프로필로 컨텍스트가 뜨고 핵심 빈이 등록된다")
    void contextLoads() {
        assertThat(context.getBean(ItemController.class)).isNotNull();
        assertThat(context.getBean(ItemService.class)).isNotNull();
    }
}
