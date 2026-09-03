import { renderHook, act } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebounced } from './useDebounced';

describe('useDebounced', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('gir startverdien med én gang', () => {
    const { result } = renderHook(() => useDebounced('a', 250));
    expect(result.current).toBe('a');
  });

  it('holder tilbake verdien til ventetiden er ute', () => {
    const { result, rerender } = renderHook(({ v }) => useDebounced(v, 250), {
      initialProps: { v: 'a' },
    });

    rerender({ v: 'ab' });
    expect(result.current).toBe('a');

    act(() => void vi.advanceTimersByTime(250));
    expect(result.current).toBe('ab');
  });

  it('nullstiller ventetiden ved rask skriving, slik at bare siste verdi slipper gjennom', () => {
    const { result, rerender } = renderHook(({ v }) => useDebounced(v, 250), {
      initialProps: { v: '' },
    });

    for (const v of ['d', 'da', 'dag']) {
      rerender({ v });
      act(() => void vi.advanceTimersByTime(200));
    }
    expect(result.current).toBe('');

    act(() => void vi.advanceTimersByTime(250));
    expect(result.current).toBe('dag');
  });
});
