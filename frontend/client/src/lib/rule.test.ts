import { describe, expect, it } from 'vitest';
import { formatRule, formatHours, parseRule, parseHoursRange } from './rule';

describe('parseRule', () => {
  it('deler uttrykket i fire felt', () => {
    expect(parseRule('??.??.???? ? 1-5 08:00-15:30')).toEqual({
      date: '??.??.????',
      dayOfMonth: '?',
      weekday: '1-5',
      time: '08:00-15:30',
    });
  });

  it('godtar vilkårlig mellomrom mellom feltene', () => {
    expect(parseRule('  ??.??.????   ?    1-5   08:00-15:30 ')?.weekday).toBe('1-5');
  });

  it('returnerer null når antall felt er feil', () => {
    expect(parseRule('??.??.???? ? 1-5')).toBeNull();
    expect(parseRule('')).toBeNull();
  });
});

describe('formatRule', () => {
  it('oversetter numeriske ukedager til norske navn', () => {
    expect(formatRule('??.??.???? ? 1-5 08:00-15:30')).toBe('Mandag–fredag, 08:00–15:30');
  });

  it('viser stengt for sentinelverdien 00:00-00:00', () => {
    expect(formatRule('??.??.???? ? 6-7 00:00-00:00')).toBe('Lørdag–søndag, stengt');
  });

  it('viser døgnåpent for sentinelverdien 00:00-23:59', () => {
    expect(formatRule('??.??.???? ? ? 00:00-23:59')).toBe('Alle dager, døgnåpent');
  });

  it('formulerer en konkret dato', () => {
    expect(formatRule('01.05.2026 ? ? 00:00-00:00')).toBe('1. mai 2026, stengt');
  });

  it('formulerer en årlig gjentakende dato', () => {
    expect(formatRule('17.05.???? ? ? 00:00-00:00')).toBe('17. mai hvert år, stengt');
  });

  it('formulerer dag i måneden', () => {
    expect(formatRule('??.??.???? L ? 08:00-14:00')).toBe(
      'Siste dag i måneden, 08:00–14:00',
    );
  });

  it('kombinerer flere ukedagstokens', () => {
    expect(formatRule('??.??.???? ? 1-4,6 09:00-16:00')).toBe(
      'Mandag–torsdag, lørdag, 09:00–16:00',
    );
  });

  it('returnerer uttrykket uendret når det ikke lar seg parse', () => {
    expect(formatRule('tull')).toBe('tull');
  });
});

describe('parseHoursRange', () => {
  it('godtar mellomrom rundt bindestreken', () => {
    expect(parseHoursRange('08:00 - 15:30')).toEqual({ open: '08:00', close: '15:30' });
  });

  it('kutter sekunder', () => {
    expect(parseHoursRange('08:00:00-15:30:00')).toEqual({ open: '08:00', close: '15:30' });
  });

  it('returnerer null for ugyldig format', () => {
    expect(parseHoursRange('08:00')).toBeNull();
  });
});

describe('formatHours', () => {
  it('bruker halvlang bindestrek i visning', () => {
    expect(formatHours('08:00-15:30')).toBe('08:00–15:30');
  });
});
