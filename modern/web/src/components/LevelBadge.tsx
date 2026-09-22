import type { Level } from '../api/types';

interface LevelBadgeProps {
  level: Level;
}

const LEVEL_LABELS: Record<Level, string> = {
  1: '매우 쉬움',
  2: '쉬움',
  3: '보통',
  4: '어려움',
  5: '매우 어려움',
};

/** 난이도(1~5)를 색상 배지로 표시한다. */
export function LevelBadge({ level }: LevelBadgeProps) {
  return (
    <span
      className={`level-badge level-badge--${level}`}
      title={LEVEL_LABELS[level]}
      aria-label={`난이도 ${level} (${LEVEL_LABELS[level]})`}
    >
      Lv.{level}
    </span>
  );
}
