import { describe, expect, it } from 'vitest';
import { isValidRule, validateRule } from './validate';

/**
 * Fasit er backendens `RuleValidator.kt`. Testene her dokumenterer grensene den
 * setter, slik at et avvik oppdages i klienten framfor som «Invalid rule format».
 */

describe('gyldige uttrykk', () => {
  it.each([
    '??.??.???? ? ? 08:00-15:30',
    '??.??.???? ? 1-5 08:00-15:30',
    '17.05.2026 ? ? 00:00-00:00',
    '17.05.???? ? ? 00:00-00:00',
    '??.05.???? ? ? 08:00-15:30',
    '??.??.???? L ? 08:00-15:30',
    '??.??.???? 1,15 ? 08:00-15:30',
    '??.??.???? 20-L ? 08:00-15:30',
    '??.??.???? ? 1-4,6 08:00-15:30',
    '??.??.???? ? 6,7 00:00-00:00',
    '??.??.???? ? ? 00:00-23:59',
  ])('godtar %s', (rule) => {
    expect(validateRule(rule)).toBeNull();
  });

  it('godtar 29. februar i skuddår', () => {
    expect(isValidRule('29.02.2024 ? ? 08:00-15:30')).toBe(true);
  });
});

describe('struktur', () => {
  it('krever fire felt', () => {
    expect(validateRule('??.??.???? ? ?')?.message).toContain('fire deler');
    expect(validateRule('??.??.???? ? ? 08:00-15:30 ekstra')?.message).toContain('fire deler');
  });

  it('avviser tomt uttrykk', () => {
    expect(validateRule('   ')?.message).toContain('kan ikke være tomt');
  });
});

describe('datofeltet', () => {
  it('avviser en dato som ikke finnes', () => {
    const error = validateRule('31.02.2026 ? ? 08:00-15:30');
    expect(error?.field).toBe('date');
    expect(error?.message).toContain('Februar 2026 har ikke 31 dager');
  });

  it('avviser 29. februar i ikke-skuddår', () => {
    expect(isValidRule('29.02.2025 ? ? 08:00-15:30')).toBe(false);
  });

  it('krever to siffer når året er oppgitt', () => {
    expect(validateRule('1.5.2026 ? ? 08:00-15:30')?.message).toContain('to siffer');
  });

  it('avviser måned utenfor 1-12', () => {
    expect(validateRule('??.13.???? ? ? 08:00-15:30')?.field).toBe('date');
  });
});

describe('dag i måneden', () => {
  it('krever stigende rekkefølge', () => {
    expect(validateRule('??.??.???? 15,1 ? 08:00-15:30')?.field).toBe('dayOfMonth');
  });

  it('avviser overlappende områder', () => {
    expect(validateRule('??.??.???? 5-12,8 ? 08:00-15:30')?.field).toBe('dayOfMonth');
  });

  it('krever at L står sist', () => {
    expect(validateRule('??.??.???? L,5 ? 08:00-15:30')?.message).toContain('L må stå sist');
    expect(validateRule('??.??.???? 20-L,25 ? 08:00-15:30')?.message).toContain('L må stå sist');
  });

  it('avviser dager utenfor 1-31', () => {
    expect(validateRule('??.??.???? 32 ? 08:00-15:30')?.field).toBe('dayOfMonth');
    expect(validateRule('??.??.???? 0 ? 08:00-15:30')?.field).toBe('dayOfMonth');
  });
});

describe('ukedagsfeltet', () => {
  it('avviser navn på ukedager, som designet foreslo', () => {
    const error = validateRule('??.??.???? ? man-fre 08:00-15:30');
    expect(error?.field).toBe('weekday');
    expect(error?.message).toContain('1 er mandag');
  });

  it('avviser dager utenfor 1-7', () => {
    expect(validateRule('??.??.???? ? 8 08:00-15:30')?.field).toBe('weekday');
    expect(validateRule('??.??.???? ? 0 08:00-15:30')?.field).toBe('weekday');
  });

  it('avviser områder med mer enn én bindestrek', () => {
    expect(validateRule('??.??.???? ? 1-3-5 08:00-15:30')?.field).toBe('weekday');
  });

  it('krever stigende, ikke-overlappende områder', () => {
    expect(validateRule('??.??.???? ? 5-1 08:00-15:30')?.field).toBe('weekday');
    expect(validateRule('??.??.???? ? 1-5,3 08:00-15:30')?.field).toBe('weekday');
  });
});

describe('klokkeslettfeltet', () => {
  it('sier tydelig fra at flere intervaller ikke støttes', () => {
    const error = validateRule('??.??.???? ? ? 08:00-11:30,12:15-15:30');
    expect(error?.field).toBe('time');
    expect(error?.message).toContain('Bare ett tidsrom per regel');
  });

  it('krever HH:mm-HH:mm', () => {
    expect(validateRule('??.??.???? ? ? 0800-1530')?.message).toBe(
      'Klokkeslettet må skrives som 08:00-15:30.',
    );
  });

  it('krever at slutt kommer etter start', () => {
    expect(validateRule('??.??.???? ? ? 15:30-08:00')?.message).toBe(
      'Sluttidspunktet må være etter starttidspunktet.',
    );
  });

  it('godtar 00:00-00:00 som «stengt»', () => {
    expect(isValidRule('??.??.???? ? ? 00:00-00:00')).toBe(true);
  });

  it('avviser timer over 23 og minutter over 59', () => {
    expect(isValidRule('??.??.???? ? ? 24:00-25:00')).toBe(false);
    expect(isValidRule('??.??.???? ? ? 08:60-15:30')).toBe(false);
  });
});
