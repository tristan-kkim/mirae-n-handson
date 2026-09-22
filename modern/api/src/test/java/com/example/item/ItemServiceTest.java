package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private ItemService itemService;

    private final Unit unit = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    @Test
    @DisplayName("getItem: 단원 코드와 태그 이름이 응답에 담긴다")
    void getItemMapsToResponse() {
        Item item = ItemFixtures.item(7, unit, "분모가 같은 분수의 덧셈", 3,
            ItemStatus.ACTIVE, ItemFixtures.tag(1, "계산"), ItemFixtures.tag(2, "문장제"));
        when(itemRepository.findWithDetailsById(7)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.getItem(7);

        assertThat(response.id()).isEqualTo(7);
        assertThat(response.unit()).isEqualTo("M5-1");
        assertThat(response.level()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("A");
        assertThat(response.tags()).containsExactly("계산", "문장제");
    }

    @Test
    @DisplayName("getItem: 없는 id 는 NotFoundException")
    void getItemThrowsWhenMissing() {
        when(itemRepository.findWithDetailsById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItem(999))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("listActiveItemsByUnit: status='A' 로만 저장소에 묻는다")
    void listActiveItemsQueriesActiveStatusOnly() {
        when(unitRepository.findByCode("M5-1")).thenReturn(Optional.of(unit));
        when(itemRepository.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE)).thenReturn(List.of(
            ItemFixtures.item(2, unit, "난이도 5 문항", 5, ItemStatus.ACTIVE),
            ItemFixtures.item(1, unit, "난이도 2 문항", 2, ItemStatus.ACTIVE)));

        List<ItemResponse> items = itemService.listActiveItemsByUnit("M5-1");

        assertThat(items).extracting(ItemResponse::id).containsExactly(2, 1);
        assertThat(items).allSatisfy(item -> assertThat(item.status()).isEqualTo(ItemStatus.ACTIVE));
        verify(itemRepository).findByUnitCodeAndStatus("M5-1", "A");
    }

    @Test
    @DisplayName("listActiveItemsByUnit: 없는 단원 코드는 NotFoundException, 문항 조회는 하지 않는다")
    void listActiveItemsThrowsWhenUnitMissing() {
        when(unitRepository.findByCode("M9-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.listActiveItemsByUnit("M9-9"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("M9-9");
        verify(itemRepository, never()).findByUnitCodeAndStatus(anyString(), anyString());
    }
}
