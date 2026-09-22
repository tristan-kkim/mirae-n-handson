import type { Item } from '../api/types';
import { LevelBadge } from './LevelBadge';
import { StatusBadge } from './StatusBadge';

interface ItemDetailProps {
  item: Item;
}

/** 문항 상세. 지문 · 단원 · 태그 · 상태를 보여준다. */
export function ItemDetail({ item }: ItemDetailProps) {
  return (
    <article className="item-detail" aria-labelledby="item-detail-title">
      <header className="item-detail__header">
        <h3 id="item-detail-title">
          #{item.id} {item.title}
        </h3>
        <div className="item-detail__badges">
          <LevelBadge level={item.level} />
          <StatusBadge status={item.status} />
        </div>
      </header>

      <dl className="item-detail__meta">
        <dt>단원</dt>
        <dd>{item.unit}</dd>
        <dt>태그</dt>
        <dd>{item.tags.length > 0 ? item.tags.join(', ') : '없음'}</dd>
      </dl>

      <section aria-label="지문">
        <h4>지문</h4>
        <p className="item-detail__stem">{item.stem}</p>
      </section>
    </article>
  );
}
