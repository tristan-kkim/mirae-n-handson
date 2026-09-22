import { fireEvent, render, screen } from '@testing-library/react';

import { itemsOfM51 } from '../test/fixtures';
import { ItemTable } from './ItemTable';

describe('ItemTable', () => {
  it('문항이 없으면 안내 문구를 보여준다', () => {
    render(<ItemTable items={[]} selectedId={null} onSelect={() => {}} />);
    expect(screen.getByText('이 단원에는 공개된 문항이 없습니다')).toBeTruthy();
  });

  it('행마다 번호 · 제목 · 난이도 · 태그를 그리고, 제목 클릭 시 onSelect(id) 를 한 번 부른다', () => {
    const onSelect = vi.fn();
    render(<ItemTable items={itemsOfM51} selectedId={2} onSelect={onSelect} />);

    expect(screen.getAllByRole('row')).toHaveLength(itemsOfM51.length + 1); // header 포함
    expect(screen.getByText('계산, 오답률높음')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy(); // 태그 없는 문항

    fireEvent.click(screen.getByRole('button', { name: '분수 덧셈 문장제' }));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(3);
  });
});
