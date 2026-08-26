import { describe, expect, it } from 'vitest';
import { previewRule, ruleMatchesDate } from './evaluate';

/**
 * Fasit er backendens `OpeningHoursEvaluator.kt`. Datoene under er valgt for å
 * treffe grensene: månedsskifte, siste dag i måneden, skuddår og helg.
 *
 * 2026-05-14 er en torsdag, 2026-05-17 en søndag, 2026-05-31 en søndag og
 * månedens siste dag.
 */

describe('datofeltet', () => {
  it('«??.??.????» treffer alle datoer', () => {
    expect(ruleMatchesDate('??.??.???? ? ? 08:00-15:30', '2026-05-14').matches).toBe(true);
  });

  it('«dd.MM.????» treffer samme dag hvert år', () => {
    expect(ruleMatchesDate('17.05.???? ? ? 00:00-00:00', '2026-05-17').matches).toBe(true);
    expect(ruleMatchesDate('17.05.???? ? ? 00:00-00:00', '2027-05-17').matches).toBe(true);
    expect(ruleMatchesDate('17.05.???? ? ? 00:00-00:00', '2026-05-18').matches).toBe(false);
  });

  it('«??.MM.????» treffer hele måneden', () => {
    expect(ruleMatchesDate('??.05.???? ? ? 08:00-15:30', '2026-05-01').matches).toBe(true);
    expect(ruleMatchesDate('??.05.???? ? ? 08:00-15:30', '2026-06-01').matches).toBe(false);
  });

  it('«dd.MM.yyyy» treffer nøyaktig én dato', () => {
    expect(ruleMatchesDate('17.05.2026 ? ? 00:00-00:00', '2026-05-17').matches).toBe(true);
    expect(ruleMatchesDate('17.05.2026 ? ? 00:00-00:00', '2027-05-17').matches).toBe(false);
  });

  it('forklarer hvilket felt som utelukket datoen', () => {
    const result = ruleMatchesDate('17.05.???? ? ? 00:00-00:00', '2026-05-18');
    expect(result.blockedBy).toBe('date');
    expect(result.reason).toContain('Datofeltet');
  });
});

describe('dag i måneden', () => {
  it('«L» treffer siste dag i måneden', () => {
    expect(ruleMatchesDate('??.??.???? L ? 08:00-15:30', '2026-05-31').matches).toBe(true);
    expect(ruleMatchesDate('??.??.???? L ? 08:00-15:30', '2026-05-30').matches).toBe(false);
  });

  it('«L» følger månedens faktiske lengde, også i skuddår', () => {
    expect(ruleMatchesDate('??.??.???? L ? 08:00-15:30', '2024-02-29').matches).toBe(true);
    expect(ruleMatchesDate('??.??.???? L ? 08:00-15:30', '2025-02-28').matches).toBe(true);
    expect(ruleMatchesDate('??.??.???? L ? 08:00-15:30', '2024-02-28').matches).toBe(false);
  });

  it('«20-L» treffer fra den 20. og ut måneden', () => {
    expect(ruleMatchesDate('??.??.???? 20-L ? 08:00-15:30', '2026-05-25').matches).toBe(true);
    expect(ruleMatchesDate('??.??.???? 20-L ? 08:00-15:30', '2026-05-19').matches).toBe(false);
  });

  it('kommaliste treffer hver av dagene', () => {
    const rule = '??.??.???? 1,15 ? 08:00-15:30';
    expect(ruleMatchesDate(rule, '2026-05-01').matches).toBe(true);
    expect(ruleMatchesDate(rule, '2026-05-15').matches).toBe(true);
    expect(ruleMatchesDate(rule, '2026-05-14').matches).toBe(false);
  });
});

describe('ukedagsfeltet', () => {
  it('1 er mandag og 7 er søndag', () => {
    expect(ruleMatchesDate('??.??.???? ? 1 08:00-15:30', '2026-05-11').matches).toBe(true); // mandag
    expect(ruleMatchesDate('??.??.???? ? 7 08:00-15:30', '2026-05-17').matches).toBe(true); // søndag
  });

  it('«1-5» treffer hverdager, ikke helg', () => {
    const rule = '??.??.???? ? 1-5 08:00-15:30';
    expect(ruleMatchesDate(rule, '2026-05-14').matches).toBe(true); // torsdag
    expect(ruleMatchesDate(rule, '2026-05-16').matches).toBe(false); // lørdag
    expect(ruleMatchesDate(rule, '2026-05-17').matches).toBe(false); // søndag
  });

  it('«6,7» treffer bare helg', () => {
    const rule = '??.??.???? ? 6,7 00:00-00:00';
    expect(ruleMatchesDate(rule, '2026-05-16').matches).toBe(true);
    expect(ruleMatchesDate(rule, '2026-05-14').matches).toBe(false);
  });

  it('navngir ukedagen som ble utelukket', () => {
    const result = ruleMatchesDate('??.??.???? ? 1-5 08:00-15:30', '2026-05-16');
    expect(result.blockedBy).toBe('weekday');
    expect(result.reason).toContain('lørdag');
  });
});

describe('rekkefølgen på feltene', () => {
  it('rapporterer datofeltet før ukedagsfeltet når begge utelukker', () => {
    const result = ruleMatchesDate('17.05.???? ? 1-5 08:00-15:30', '2026-05-16');
    expect(result.blockedBy).toBe('date');
  });
});

describe('previewRule', () => {
  it('gir åpningstidene når regelen treffer', () => {
    const preview = previewRule('??.??.???? ? 1-5 08:00-15:30', '2026-05-14');
    expect(preview.matches).toBe(true);
    expect(preview.hours).toEqual({ open: '08:00', close: '15:30' });
    expect(preview.closed).toBe(false);
    expect(preview.allDay).toBe(false);
  });

  it('gjenkjenner «stengt»-sentinelen', () => {
    const preview = previewRule('17.05.???? ? ? 00:00-00:00', '2026-05-17');
    expect(preview.matches).toBe(true);
    expect(preview.closed).toBe(true);
    expect(preview.hours).toBeNull();
  });

  it('gjenkjenner døgnåpent', () => {
    const preview = previewRule('??.??.???? ? ? 00:00-23:59', '2026-05-14');
    expect(preview.allDay).toBe(true);
  });

  it('gir ingen åpningstider når regelen ikke traff', () => {
    const preview = previewRule('??.??.???? ? 1-5 08:00-15:30', '2026-05-16');
    expect(preview.matches).toBe(false);
    expect(preview.hours).toBeNull();
  });

  it('takler et uttrykk som ikke lar seg tolke', () => {
    const preview = previewRule('tull', '2026-05-14');
    expect(preview.matches).toBe(false);
    expect(preview.reason).toContain('kunne ikke tolkes');
  });
});
