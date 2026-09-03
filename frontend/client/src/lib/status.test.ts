import { describe, expect, it } from 'vitest';
import type { QueryResponse } from '../api/types';
import { deriveStatus, statusAriaLabel, toIntervals } from './status';

function day(overrides: Partial<QueryResponse> = {}): QueryResponse {
  return {
    resourceId: 'a',
    date: '2026-05-12',
    isOpen: true,
    openingTime: '08:00',
    closingTime: '15:30',
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    ...overrides,
  };
}

describe('toIntervals', () => {
  it('gir ett intervall — backend støtter ikke flere per dag', () => {
    expect(toIntervals(day())).toEqual([{ from: 480, to: 930 }]);
  });

  it('gir ingen intervaller når dagen er stengt', () => {
    expect(toIntervals(day({ openingTime: '00:00', closingTime: '00:00' }))).toEqual([]);
  });

  it('gir ingen intervaller når sluttid ikke er etter starttid', () => {
    expect(toIntervals(day({ openingTime: '15:00', closingTime: '09:00' }))).toEqual([]);
  });

  it('takler sekunder fra backend', () => {
    expect(toIntervals(day({ openingTime: '08:00:00', closingTime: '15:30:00' }))).toEqual([
      { from: 480, to: 930 },
    ]);
  });
});

describe('deriveStatus', () => {
  it('markerer åpen dag med klokkeslett', () => {
    const status = deriveStatus(day());
    expect(status.kind).toBe('open');
    expect(status.label).toBe('Åpen 08:00–15:30');
  });

  it('markerer døgnåpen', () => {
    const status = deriveStatus(day({ openingTime: '00:00', closingTime: '23:59' }));
    expect(status.kind).toBe('open');
    expect(status.label).toBe('Døgnåpen');
    expect(status.allDay).toBe(true);
  });

  it('forklarer stengt i helg', () => {
    const status = deriveStatus(
      day({ date: '2026-05-16', openingTime: '00:00', closingTime: '00:00' }),
    );
    expect(status.kind).toBe('closed');
    expect(status.detail).toBe('Helg');
  });

  it('setter navn på rød dag selv om API-et ikke sender det', () => {
    const status = deriveStatus(
      day({ date: '2026-05-17', redDay: true, openingTime: '00:00', closingTime: '00:00' }),
    );
    expect(status.kind).toBe('redDay');
    expect(status.holiday).toBe('Grunnlovsdagen');
    expect(status.detail).toBe('Grunnlovsdagen · stengt');
  });

  it('faller tilbake til «Stengt» når rød dag ikke er en offisiell helligdag', () => {
    const status = deriveStatus(day({ date: '2026-05-12', redDay: true }));
    expect(status.kind).toBe('redDay');
    expect(status.holiday).toBeNull();
    expect(status.detail).toBe('Stengt');
  });

  it('regner en dag uten gjeldende regel som døgnåpen', () => {
    // Backend svarer med 00:00–23:59 når ingen regel treffer, og det er den
    // avtalte betydningen: uten regler er tjenesten åpen, ikke ukjent.
    const status = deriveStatus(
      day({ openingTime: '00:00', closingTime: '23:59', warningMessage: 'Ingen gruppe' }),
    );
    expect(status.kind).toBe('open');
    expect(status.allDay).toBe(true);
  });

  it('lar maskering gå foran alt annet', () => {
    const status = deriveStatus(day({ masked: true, redDay: true, warningMessage: 'x' }));
    expect(status.kind).toBe('masked');
    expect(status.intervals).toEqual([]);
  });
});

describe('statusAriaLabel', () => {
  it('beskriver hele statusen i tekst, ikke bare farge', () => {
    const d = day();
    expect(statusAriaLabel(d, deriveStatus(d))).toBe(
      'tirsdag 12. mai 2026, åpen 08:00 til 15:30',
    );
  });

  it('nevner navnet på den røde dagen', () => {
    const d = day({ date: '2026-05-17', redDay: true, openingTime: '00:00', closingTime: '00:00' });
    expect(statusAriaLabel(d, deriveStatus(d))).toContain('grunnlovsdagen');
  });
});

describe('ustabile perioder', () => {
  it('er avslått som standard', () => {
    expect(deriveStatus(day()).unstable).toBe(false);
  });

  it('kommer i tillegg til statusen, ikke i stedet for den', () => {
    const status = deriveStatus(day({ unstableOpeningHours: true }));
    expect(status.unstable).toBe(true);
    // Åpningstiden gjelder fortsatt — flagget sier bare at den kan svikte.
    expect(status.kind).toBe('open');
    expect(status.label).toBe('Åpen 08:00–15:30');
    expect(status.intervals).toHaveLength(1);
  });

  it('kan settes på en stengt dag også', () => {
    const status = deriveStatus(
      day({ unstableOpeningHours: true, openingTime: '00:00', closingTime: '00:00' }),
    );
    expect(status.kind).toBe('closed');
    expect(status.unstable).toBe(true);
  });

  it('havner i aria-etiketten, slik at merket ikke bare er visuelt', () => {
    const d = day({ unstableOpeningHours: true });
    expect(statusAriaLabel(d, deriveStatus(d))).toContain('merket som ustabil periode');
  });

  it('nevnes ikke i aria-etiketten når flagget er av', () => {
    const d = day();
    expect(statusAriaLabel(d, deriveStatus(d))).not.toContain('ustabil');
  });
});
