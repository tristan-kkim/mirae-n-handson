import { fireEvent, render, screen } from '@testing-library/react';

import { item2, itemsOfM51, units } from '../test/fixtures';
import { mockFetch, mockFetchNetworkError } from '../test/mockFetch';
import { ItemBrowser } from './ItemBrowser';

describe('ItemBrowser (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('단원 → 문항 → 상세 순서로 API 를 호출해 화면을 채운다', async () => {
    const fetchSpy = mockFetch({
      '/api/units': { body: units },
      '/api/units/M5-1/items': { body: itemsOfM51 },
      '/api/items/2': { body: item2 },
    });

    render(<ItemBrowser />);

    // 1) 단원 목록
    expect(await screen.findByText('분수의 덧셈과 뺄셈')).toBeTruthy();
    expect(screen.getByText('왼쪽에서 단원을 선택하세요')).toBeTruthy();

    // 2) 단원 선택 → 문항 목록
    fireEvent.click(screen.getByRole('button', { name: /M5-1/ }));
    expect(await screen.findByText('분모가 다른 분수의 뺄셈')).toBeTruthy();
    expect(screen.getByRole('heading', { name: '문항 — M5-1' })).toBeTruthy();

    // 3) 문항 선택 → 상세
    fireEvent.click(screen.getByRole('button', { name: '분모가 다른 분수의 뺄셈' }));
    expect(await screen.findByText('5/6 - 1/4 를 계산하시오.')).toBeTruthy();
    expect(screen.getByText('공개')).toBeTruthy();

    const calledPaths = fetchSpy.mock.calls.map(([input]) => new URL(String(input)).pathname);
    expect(calledPaths).toEqual(['/api/units', '/api/units/M5-1/items', '/api/items/2']);
  });

  it('API 서버에 연결할 수 없으면 오류 문구를 보여준다', async () => {
    mockFetchNetworkError();

    render(<ItemBrowser />);

    expect((await screen.findByRole('alert')).textContent).toBe('API 서버에 연결할 수 없습니다');
  });
});
