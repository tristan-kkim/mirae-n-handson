import { useItem } from '../hooks/useItem';
import { AsyncSection } from './AsyncSection';
import { ItemDetail } from './ItemDetail';

interface ItemDetailPanelProps {
  itemId: number | null;
}

/** itemId 로 상세를 조회해 ItemDetail 로 그린다. null 이면 안내 문구만. */
export function ItemDetailPanel({ itemId }: ItemDetailPanelProps) {
  const item = useItem(itemId);

  return (
    <AsyncSection state={item} idleMessage="문항을 선택하면 상세가 표시됩니다">
      {(data) => <ItemDetail item={data} />}
    </AsyncSection>
  );
}
