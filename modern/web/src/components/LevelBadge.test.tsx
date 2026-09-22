import { render, screen } from '@testing-library/react';

import { LevelBadge } from './LevelBadge';

describe('LevelBadge', () => {
  it('난이도 숫자와 라벨을 표시한다', () => {
    render(<LevelBadge level={5} />);
    const badge = screen.getByText('Lv.5');
    expect(badge.getAttribute('aria-label')).toBe('난이도 5 (매우 어려움)');
    expect(badge.className).toContain('level-badge--5');
  });
});
