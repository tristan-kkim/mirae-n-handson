package com.example.item;

import com.example.common.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 조회 서비스. 엔티티는 트랜잭션 안에서 DTO로 바꿔 돌려준다(OSIV 꺼져 있음).
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;
    private final UnitRepository unitRepository;

    public ItemService(ItemRepository itemRepository, UnitRepository unitRepository) {
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
    }

    /** 문항 단건. 상태와 무관하게 id로 찾는다(관리 화면용). */
    public ItemResponse getItem(Integer id) {
        Item item = itemRepository.findWithDetailsById(id)
            .orElseThrow(() -> new NotFoundException("문항이 없습니다: id=" + id));
        return ItemResponse.from(item);
    }

    /** 단원의 공개({@code status='A'}) 문항 목록. 단원 코드가 없으면 404. */
    public List<ItemResponse> listActiveItemsByUnit(String unitCode) {
        Unit unit = unitRepository.findByCode(unitCode)
            .orElseThrow(() -> new NotFoundException("단원이 없습니다: code=" + unitCode));
        List<ItemResponse> items = itemRepository.findByUnitCodeAndStatus(unit.getCode(), ItemStatus.ACTIVE).stream()
            .map(ItemResponse::from)
            .toList();
        log.debug("unit {} has {} active items", unit.getCode(), items.size());
        return items;
    }
}
