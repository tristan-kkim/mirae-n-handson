import { useState } from 'react';

import { useUnitItems } from '../hooks/useUnitItems';
import { useUnits } from '../hooks/useUnits';
import { AsyncSection } from './AsyncSection';
import { ItemDetailPanel } from './ItemDetailPanel';
import { ItemTable } from './ItemTable';
import { UnitList } from './UnitList';

/**
 * 단원 → 문항 → 상세 3단 탐색 화면.
 * 데이터 조회는 훅(src/hooks)에, 그리기는 프레젠테이션 컴포넌트에 맡기고
 * 여기서는 선택 상태만 관리한다.
 */
export function ItemBrowser() {
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [selectedItemId, setSelectedItemId] = useState<number | null>(null);

  const units = useUnits();
  const items = useUnitItems(selectedCode);

  const handleSelectUnit = (code: string) => {
    setSelectedCode(code);
    setSelectedItemId(null);
  };

  return (
    <div className="browser">
      <section className="browser__pane" aria-labelledby="units-heading">
        <h2 id="units-heading">단원</h2>
        <AsyncSection state={units}>
          {(data) => <UnitList units={data} selectedCode={selectedCode} onSelect={handleSelectUnit} />}
        </AsyncSection>
      </section>

      <section className="browser__pane browser__pane--wide" aria-labelledby="items-heading">
        <h2 id="items-heading">문항{selectedCode ? ` — ${selectedCode}` : ''}</h2>
        <AsyncSection state={items} idleMessage="왼쪽에서 단원을 선택하세요">
          {(data) => <ItemTable items={data} selectedId={selectedItemId} onSelect={setSelectedItemId} />}
        </AsyncSection>
      </section>

      <section className="browser__pane browser__pane--wide" aria-labelledby="detail-heading">
        <h2 id="detail-heading">상세</h2>
        <ItemDetailPanel itemId={selectedItemId} />
      </section>
    </div>
  );
}
