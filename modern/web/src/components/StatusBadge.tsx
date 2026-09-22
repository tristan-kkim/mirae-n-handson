import type { ItemStatus } from '../api/types';

interface StatusBadgeProps {
  status: ItemStatus;
}

const STATUS_LABELS: Record<ItemStatus, string> = {
  A: '공개',
  D: '삭제',
  R: '검수중',
};

/** 문항 상태 코드(A/D/R)를 한글 배지로 표시한다. */
export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`status-badge status-badge--${status}`} aria-label={`상태 ${STATUS_LABELS[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}
