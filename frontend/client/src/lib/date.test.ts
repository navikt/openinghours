import { describe, expect, it } from 'vitest';
import {
  addDays,
  dateToIso,
  formatMonthName,
  formatWeek,
  isoToDate,
  isoWeekday,
  isoWeekNumber,
  lastOfMonth,
  monthGrid,
  monthsOfYear,
  shiftMonth,
  shiftWeek,
  startOfWeek,
  todayIso,
  weekDays,
} from './date';

describe('shiftMonth', () => {
  it('krysser årsskiftet fremover', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
  });

  it('krysser årsskiftet bakover', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
  });

  it('flytter flere år', () => {
    expect(shiftMonth('2026-05', 24)).toBe('2028-05');
  });
});

describe('lastOfMonth', () => {
  it('takler februar i skuddår', () => {
    expect(lastOfMonth('2024-02')).toBe('2024-02-29');
  });

  it('takler februar i normalår', () => {
    expect(lastOfMonth('2026-02')).toBe('2026-02-28');
  });

  it('takler måneder med 30 dager', () => {
    expect(lastOfMonth('2026-04')).toBe('2026-04-30');
  });
});

describe('isoWeekday', () => {
  it('gir 1 for mandag og 7 for søndag', () => {
    expect(isoWeekday('2026-05-11')).toBe(1);
    expect(isoWeekday('2026-05-17')).toBe(7);
  });
});

describe('addDays', () => {
  it('krysser sommertidsovergangen uten å hoppe over en dag', () => {
    // Norge går til sommertid natt til 29. mars 2026.
    expect(addDays('2026-03-28', 1)).toBe('2026-03-29');
    expect(addDays('2026-03-29', 1)).toBe('2026-03-30');
  });

  it('krysser overgangen til vintertid', () => {
    expect(addDays('2026-10-24', 1)).toBe('2026-10-25');
    expect(addDays('2026-10-25', 1)).toBe('2026-10-26');
  });
});

describe('monthGrid', () => {
  it('starter på mandag og fyller hele uker', () => {
    const grid = monthGrid('2026-05');
    expect(grid.length % 7).toBe(0);
    expect(isoWeekday(grid[0].date)).toBe(1);
    expect(isoWeekday(grid[grid.length - 1].date)).toBe(7);
  });

  it('inneholder alle dagene i måneden', () => {
    const grid = monthGrid('2026-05');
    expect(grid.filter((d) => d.inMonth)).toHaveLength(31);
  });

  it('markerer dager fra nabomånedene', () => {
    const grid = monthGrid('2026-05');
    expect(grid[0].inMonth).toBe(false);
    expect(grid[0].date).toBe('2026-04-27');
  });

  it('takler februar i skuddår', () => {
    expect(monthGrid('2024-02').filter((d) => d.inMonth)).toHaveLength(29);
  });
});

describe('todayIso', () => {
  it('bruker Oslo-tid, ikke klientens tidssone', () => {
    // 23:30 UTC 11. mai er allerede 12. mai i Oslo (sommertid, UTC+2).
    expect(todayIso(new Date('2026-05-11T23:30:00Z'))).toBe('2026-05-12');
  });
});

describe('startOfWeek og weekDays', () => {
  it('finner mandagen i uka', () => {
    expect(startOfWeek('2025-08-21')).toBe('2025-08-18'); // torsdag → mandag
    expect(startOfWeek('2025-08-18')).toBe('2025-08-18'); // mandag → seg selv
    expect(startOfWeek('2025-08-24')).toBe('2025-08-18'); // søndag hører til uka før
  });

  it('krysser månedsskillet', () => {
    expect(startOfWeek('2025-09-02')).toBe('2025-09-01');
    expect(startOfWeek('2025-03-01')).toBe('2025-02-24');
  });

  it('gir sju datoer fra mandag til søndag', () => {
    const days = weekDays('2025-08-18');
    expect(days).toHaveLength(7);
    expect(days[0]).toBe('2025-08-18');
    expect(days[6]).toBe('2025-08-24');
  });

  it('flytter en uke om gangen over årsskiftet', () => {
    expect(shiftWeek('2025-12-29', 1)).toBe('2026-01-05');
    expect(shiftWeek('2026-01-05', -1)).toBe('2025-12-29');
  });
});

describe('isoWeekNumber', () => {
  it('følger ISO-8601', () => {
    expect(isoWeekNumber('2025-08-21')).toBe(34);
    expect(isoWeekNumber('2026-01-01')).toBe(1);
  });

  it('legger nyttårsdagen i foregående års siste uke når den faller tidlig i uka', () => {
    // 1. januar 2023 var en søndag og tilhører uke 52 i 2022.
    expect(isoWeekNumber('2023-01-01')).toBe(52);
    // 31. desember 2024 var en tirsdag og tilhører uke 1 i 2025.
    expect(isoWeekNumber('2024-12-31')).toBe(1);
  });

  it('gir uke 53 i år som har det', () => {
    expect(isoWeekNumber('2020-12-31')).toBe(53);
  });
});

describe('formatWeek', () => {
  it('viser én måned når uka ligger innenfor den', () => {
    expect(formatWeek('2025-08-18')).toBe('Uke 34 · 18.–24. august 2025');
  });

  it('viser begge månedene når uka krysser skillet', () => {
    expect(formatWeek('2025-09-29')).toBe('Uke 40 · 29. september–5. oktober 2025');
  });
});

describe('monthsOfYear', () => {
  it('gir tolv måneder', () => {
    const months = monthsOfYear('2025');
    expect(months).toHaveLength(12);
    expect(months[0]).toBe('2025-01');
    expect(months[11]).toBe('2025-12');
  });
});

describe('formatMonthName', () => {
  it('gir månedsnavn med stor forbokstav uten årstall', () => {
    expect(formatMonthName('2025-05')).toBe('Mai');
  });
});

describe('isoToDate og dateToIso', () => {
  it('gir samme dato tilbake', () => {
    expect(dateToIso(isoToDate('2026-05-17'))).toBe('2026-05-17');
  });

  it('bygger en lokal dato, ikke en UTC-dato', () => {
    // Kjernen i hvorfor toISOString() ikke kan brukes: feltene må leses lokalt,
    // ellers bikker datoen én dag for klienter vest for Greenwich.
    const d = isoToDate('2026-05-17');
    expect(d.getFullYear()).toBe(2026);
    expect(d.getMonth()).toBe(4);
    expect(d.getDate()).toBe(17);
  });

  it('nullpadder måned og dag', () => {
    expect(dateToIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('overlever et årsskifte ved midnatt', () => {
    expect(dateToIso(isoToDate('2027-01-01'))).toBe('2027-01-01');
  });
});
