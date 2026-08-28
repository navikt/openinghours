import { describe, expect, it } from 'vitest';
import { parseView, shiftAnchor, viewRange } from './view';

describe('parseView', () => {
  it('godtar de tre kjente visningene', () => {
    expect(parseView('maned')).toBe('maned');
    expect(parseView('uke')).toBe('uke');
    expect(parseView('aar')).toBe('aar');
  });

  it('faller tilbake på måned ved ukjent eller manglende verdi', () => {
    expect(parseView(null)).toBe('maned');
    expect(parseView('tull')).toBe('maned');
  });
});

describe('viewRange', () => {
  it('spør om hele måneden uansett hvilken dato i måneden ankeret er', () => {
    const a = viewRange('maned', '2025-08-21');
    const b = viewRange('maned', '2025-08-01');
    expect(a).toEqual(b);
    expect(a.from).toBe('2025-08-01');
    expect(a.to).toBe('2025-08-31');
    expect(a.title).toBe('August 2025');
  });

  it('spør om mandag til søndag i ukevisning', () => {
    const range = viewRange('uke', '2025-08-21'); // torsdag
    expect(range.from).toBe('2025-08-18');
    expect(range.to).toBe('2025-08-24');
  });

  it('spør om hele året i årsvisning, også i skuddår', () => {
    expect(viewRange('aar', '2024-06-15')).toMatchObject({
      from: '2024-01-01',
      to: '2024-12-31',
      title: '2024',
    });
  });

  it('takler februar som siste dag i måneden', () => {
    expect(viewRange('maned', '2024-02-10').to).toBe('2024-02-29');
    expect(viewRange('maned', '2025-02-10').to).toBe('2025-02-28');
  });
});

describe('shiftAnchor', () => {
  it('flytter én måned og normaliserer til den første', () => {
    expect(shiftAnchor('maned', '2025-08-21', 1)).toBe('2025-09-01');
    expect(shiftAnchor('maned', '2025-01-15', -1)).toBe('2024-12-01');
  });

  it('flytter én uke fra mandagen, ikke fra ankeret', () => {
    expect(shiftAnchor('uke', '2025-08-21', 1)).toBe('2025-08-25');
    expect(shiftAnchor('uke', '2025-08-21', -1)).toBe('2025-08-11');
  });

  it('flytter ett år', () => {
    expect(shiftAnchor('aar', '2025-08-21', 1)).toBe('2026-01-01');
    expect(shiftAnchor('aar', '2025-08-21', -1)).toBe('2024-01-01');
  });

  it('krysser årsskiftet i ukevisning', () => {
    expect(shiftAnchor('uke', '2025-12-31', 1)).toBe('2026-01-05');
  });
});
