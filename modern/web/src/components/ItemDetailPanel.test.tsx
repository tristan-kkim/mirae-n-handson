import { render, screen } from '@testing-library/react';

import { item2 } from '../test/fixtures';
import { mockFetch } from '../test/mockFetch';
import { ItemDetailPanel } from './ItemDetailPanel';

describe('ItemDetailPanel (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('itemId 가 null 이면 조회하지 않고 안내 문구만 보여준다', () => {
    const fetchSpy = mockFetch({});
    render(<ItemDetailPanel itemId={null} />);

    expect(screen.getByText('문항을 선택하면 상세가 표시됩니다')).toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('GET /api/items/{id} 결과를 상세로 그린다', async () => {
    mockFetch({ '/api/items/2': { body: item2 } });
    render(<ItemDetailPanel itemId={2} />);

    expect((screen.getByRole('status')).textContent).toBe('불러오는 중…');
    expect(await screen.findByRole('heading', { name: '#2 분모가 다른 분수의 뺄셈' })).toBeTruthy();
    expect(screen.getByText('계산, 오답률높음')).toBeTruthy();
    expect(screen.getByText('M5-1', { selector: 'dd' })).toBeTruthy();
    expect(screen.getByText('5/6 - 1/4 를 계산하시오.')).toBeTruthy();
    expect(screen.getByText('공개')).toBeTruthy();
  });

  it('404 면 "요청한 데이터가 없습니다" 를 보여준다', async () => {
    mockFetch({});
    render(<ItemDetailPanel itemId={999} />);

    expect((await screen.findByRole('alert')).textContent).toBe('요청한 데이터가 없습니다');
  });
});
