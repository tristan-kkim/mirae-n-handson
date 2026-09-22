import type { Item } from '../api/types';
import { LevelBadge } from './LevelBadge';

interface ItemTableProps {
  items: Item[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}

/** 단원별 문항 표. 행을 클릭하면 onSelect(id) 를 부른다. */
export function ItemTable({ items, selectedId, onSelect }: ItemTableProps) {
  if (items.length === 0) {
    return <p className="hint">이 단원에는 공개된 문항이 없습니다</p>;
  }

  return (
    <table className="item-table">
      <caption className="sr-only">문항 목록 ({items.length}건)</caption>
      <thead>
        <tr>
          <th scope="col">번호</th>
          <th scope="col">제목</th>
          <th scope="col">난이도</th>
          <th scope="col">태그</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr
            key={item.id}
            className={item.id === selectedId ? 'item-table__row--selected' : undefined}
            aria-selected={item.id === selectedId}
          >
            <td>{item.id}</td>
            <td>
              <button type="button" className="link-button" onClick={() => onSelect(item.id)}>
                {item.title}
              </button>
            </td>
            <td>
              <LevelBadge level={item.level} />
            </td>
            <td>{item.tags.length > 0 ? item.tags.join(', ') : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
