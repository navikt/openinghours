import { describe, expect, it } from 'vitest';
import { easterSunday, holidayName } from './holidays';

describe('easterSunday', () => {
  // Fasit fra kjente påskedatoer — samme algoritme som backendens.
  it.each([
    [2024, '2024-03-31'],
    [2025, '2025-04-20'],
    [2026, '2026-04-05'],
    [2027, '2027-03-28'],
    [2030, '2030-04-21'],
  ])('regner ut første påskedag for %i', (year, expected) => {
    expect(easterSunday(year)).toBe(expected);
  });
});

describe('holidayName', () => {
  it('kjenner de faste helligdagene', () => {
    expect(holidayName('2026-01-01')).toBe('Første nyttårsdag');
    expect(holidayName('2026-05-01')).toBe('Arbeidernes dag');
    expect(holidayName('2026-05-17')).toBe('Grunnlovsdagen');
    expect(holidayName('2026-12-25')).toBe('Første juledag');
    expect(holidayName('2026-12-26')).toBe('Andre juledag');
  });

  it('kjenner de påskerelaterte helligdagene', () => {
    expect(holidayName('2026-04-02')).toBe('Skjærtorsdag');
    expect(holidayName('2026-04-03')).toBe('Langfredag');
    expect(holidayName('2026-04-05')).toBe('Første påskedag');
    expect(holidayName('2026-04-06')).toBe('Andre påskedag');
    expect(holidayName('2026-05-14')).toBe('Kristi himmelfartsdag');
    expect(holidayName('2026-05-24')).toBe('Første pinsedag');
    expect(holidayName('2026-05-25')).toBe('Andre pinsedag');
  });

  it('regner ikke søndager som helligdager — det gjør heller ikke backend', () => {
    expect(holidayName('2026-05-10')).toBeNull();
  });

  it('returnerer null for en vanlig dag', () => {
    expect(holidayName('2026-05-12')).toBeNull();
  });
});
