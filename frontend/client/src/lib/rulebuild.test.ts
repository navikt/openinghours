import { describe, expect, it } from 'vitest';
import { isValidRule } from './validate';
import {
  RULE_PATTERNS,
  buildRule,
  EMPTY_FIELDS,
  fieldsMatchRule,
  toFields,
  type RuleFields,
} from './rulebuild';

const fields = (partial: Partial<RuleFields>): RuleFields => ({ ...EMPTY_FIELDS, ...partial });

describe('buildRule', () => {
  it('tomme felt gir wildcards og «stengt»', () => {
    expect(buildRule(EMPTY_FIELDS)).toBe('??.??.???? ? ? 00:00-00:00');
  });

  it('fyller ut år når brukeren bare skriver dag og måned', () => {
    expect(buildRule(fields({ date: '17.05' }))).toBe('17.05.???? ? ? 00:00-00:00');
  });

  it('tolker ett tall som måned', () => {
    expect(buildRule(fields({ date: '7' }))).toBe('??.07.???? ? ? 00:00-00:00');
  });

  it('beholder et fullt oppgitt år', () => {
    expect(buildRule(fields({ date: '17.05.2026' }))).toBe('17.05.2026 ? ? 00:00-00:00');
  });

  it('nullpadder ensifret dag og måned', () => {
    expect(buildRule(fields({ date: '1.5.2026' }))).toBe('01.05.2026 ? ? 00:00-00:00');
  });

  it('oversetter «siste» til L', () => {
    expect(buildRule(fields({ dayOfMonth: 'siste' }))).toBe('??.??.???? L ? 00:00-00:00');
    expect(buildRule(fields({ dayOfMonth: '20-siste' }))).toBe('??.??.???? 20-L ? 00:00-00:00');
  });

  it('normaliserer mellomrom rundt bindestreken i klokkeslettet', () => {
    expect(buildRule(fields({ time: '08:00 - 15:30' }))).toBe('??.??.???? ? ? 08:00-15:30');
  });

  it('bygger et gyldig uttrykk av et realistisk sett felt', () => {
    const rule = buildRule(fields({ weekday: '1-5', time: '08:00-15:30' }));
    expect(rule).toBe('??.??.???? ? 1-5 08:00-15:30');
    expect(isValidRule(rule)).toBe(true);
  });
});

describe('toFields', () => {
  it('tømmer feltene for wildcards', () => {
    expect(toFields('??.??.???? ? ? 00:00-00:00')).toEqual(EMPTY_FIELDS);
  });

  it('viser «siste» framfor L', () => {
    expect(toFields('??.??.???? 20-L ? 08:00-15:30')?.dayOfMonth).toBe('20-siste');
  });

  it('fjerner årsplassholderen fra datofeltet', () => {
    expect(toFields('17.05.???? ? ? 00:00-00:00')?.date).toBe('17.05');
    expect(toFields('??.05.???? ? ? 08:00-15:30')?.date).toBe('05');
    expect(toFields('17.05.2026 ? ? 00:00-00:00')?.date).toBe('17.05.2026');
  });

  it('gir null når uttrykket ikke kan tolkes', () => {
    expect(toFields('tull')).toBeNull();
    expect(toFields('17-05 ? ? 08:00-15:30')).toBeNull();
  });
});

describe('rundtur', () => {
  it.each([
    '??.??.???? ? ? 00:00-00:00',
    '??.??.???? ? 1-5 08:00-15:30',
    '17.05.???? ? ? 00:00-00:00',
    '17.05.2026 ? ? 08:00-15:30',
    '??.05.???? ? ? 08:00-15:30',
    '??.??.???? L ? 08:00-15:30',
    '??.??.???? 20-L 6,7 00:00-23:59',
  ])('%s overlever begge veier', (rule) => {
    const f = toFields(rule);
    expect(f).not.toBeNull();
    expect(buildRule(f!)).toBe(rule);
  });
});

describe('fieldsMatchRule', () => {
  it('oppdager at strengen er redigert manuelt', () => {
    const f = fields({ weekday: '1-5', time: '08:00-15:30' });
    expect(fieldsMatchRule(f, '??.??.???? ? 1-5 08:00-15:30')).toBe(true);
    expect(fieldsMatchRule(f, '??.??.???? ? 1-5 09:00-15:30')).toBe(false);
  });
});

describe('mønstrene', () => {
  it('er alle gyldige regeluttrykk', () => {
    for (const pattern of RULE_PATTERNS) {
      expect(isValidRule(buildRule(pattern.fields)), pattern.label).toBe(true);
    }
  });

  it('har unike id-er', () => {
    const ids = RULE_PATTERNS.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
