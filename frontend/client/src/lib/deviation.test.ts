import { describe, expect, it } from 'vitest';
import type { QueryResponse } from '../api/types';
import {
  buildCalendar,
  deriveBaseline,
  describeSignature,
  deviationOf,
  isBaselineRule,
  signatureKey,
  signatureOf,
  upcoming,
} from './deviation';

const BASELINE_RULE = '??.??.???? ? 1-5 08:00-15:30';

function day(date: string, hours: string | 'uten regel', extra: Partial<QueryResponse> = {}): QueryResponse {
  if (hours === 'uten regel') {
    return {
      resourceId: 's1',
      date,
      isOpen: false,
      openingTime: '00:00',
      closingTime: '23:59',
      displayHeader: null,
      displayText: null,
      onlyShowForNavEmployees: false,
      unstableOpeningHours: false,
      redDay: false,
      warningMessage: 'Ingen regel treffer',
      ...extra,
    };
  }
  const [open, close] = hours.split('-');
  return {
    resourceId: 's1',
    date,
    isOpen: true,
    openingTime: open,
    closingTime: close,
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    matchedRule: { name: 'Normal', rule: BASELINE_RULE },
    ...extra,
  };
}

/** Fire hverdager på rad i mai 2025: mandag 5. til torsdag 8. */
const NORMAL_WEEK = ['2025-05-05', '2025-05-06', '2025-05-07', '2025-05-08'];

describe('signatureOf', () => {
  it('skiller åpne, stengte, døgnåpne og manglende dager', () => {
    expect(signatureKey(signatureOf(day('2025-05-05', '08:00-15:30')))).toBe('open:480-930');
    expect(signatureKey(signatureOf(day('2025-05-05', '00:00-00:00')))).toBe('closed');
    expect(signatureKey(signatureOf(day('2025-05-05', '00:00-23:59')))).toBe('allDay');
    // Traff ingen regel svarer backend med døgnåpent, og frontenden er enig:
    // en tjeneste uten oppsett er åpen, ikke ukjent.
    expect(signatureKey(signatureOf(day('2025-05-05', 'uten regel')))).toBe('allDay');
  });

  it('holder på åpningstider som krysser midnatt', () => {
    // `toIntervals` forkaster disse (`to <= from`) og ville gitt «stengt» —
    // og da hadde en ekte stenging sett identisk ut med normalen, slik at
    // stengingen aldri ble meldt.
    expect(signatureKey(signatureOf(day('2025-05-05', '22:00-02:00')))).toBe('open:1320-120');
    expect(signatureKey(signatureOf(day('2025-05-05', '22:00-02:00')))).not.toBe('closed');
  });

  it('lar rød dag arve signaturen fra åpningstiden', () => {
    // En rød dag der tjenesten er stengt ser ut som enhver annen stengt dag.
    // Det er sammenligningen med normalen som avgjør om den er verdt å nevne.
    const red = day('2025-05-17', '00:00-00:00', { redDay: true });
    expect(signatureKey(signatureOf(red))).toBe('closed');
  });
});

describe('isBaselineRule', () => {
  it('godtar ukesregler som gjentar seg uten ende', () => {
    expect(isBaselineRule('??.??.???? ? 1-5 08:00-15:30')).toBe(true);
    expect(isBaselineRule('??.??.???? ? ? 00:00-23:59')).toBe(true);
  });

  it('avviser regler festet til en dato eller en dag i måneden', () => {
    expect(isBaselineRule('24.12.???? ? ? 08:00-12:00')).toBe(false);
    expect(isBaselineRule('17.05.2025 ? ? 00:00-00:00')).toBe(false);
    expect(isBaselineRule('??.??.???? L ? 08:00-12:00')).toBe(false);
    expect(isBaselineRule('??.12.???? ? ? 08:00-12:00')).toBe(false);
  });

  it('avviser noe som ikke er et regeluttrykk', () => {
    expect(isBaselineRule('tull')).toBe(false);
  });
});

describe('deriveBaseline', () => {
  it('tar normalen fra grunnregelen selv etter én dag', () => {
    const base = deriveBaseline([day('2025-05-05', '08:00-15:30')]);
    expect(signatureKey(base.byWeekday.get(1)!)).toBe('open:480-930');
  });

  it('lar grunnregelen vinne over en avvikende dag på samme ukedag', () => {
    // Første mandag er kortdag, de neste to er normale. Uten prioriteringen av
    // grunnregelen ville en enkelt uke med to kortdager flyttet «normalen».
    const base = deriveBaseline([
      day('2025-05-05', '08:00-12:00', { matchedRule: { name: 'Kortdag', rule: '05.05.???? ? ? 08:00-12:00' } }),
      day('2025-05-12', '08:00-15:30'),
      day('2025-05-19', '08:00-15:30'),
    ]);
    expect(signatureKey(base.byWeekday.get(1)!)).toBe('open:480-930');
  });

  it('gjør «ingen regel treffer» til normalen for lørdager uten oppsett', () => {
    // Selve poenget med modulen. Mange tjenester har ingen helgeregel i det
    // hele tatt. Uten dette ville hver eneste lørdag blitt et rødt avvik.
    // Lørdagene teller som døgnåpne, og fordi de er det *hver* lørdag, er det
    // normalen — ingenting å melde.
    const days = [
      ...NORMAL_WEEK.map((d) => day(d, '08:00-15:30')),
      day('2025-05-10', 'uten regel'),
      day('2025-05-17', 'uten regel'),
      day('2025-05-24', 'uten regel'),
    ];
    const base = deriveBaseline(days);
    expect(signatureKey(base.byWeekday.get(6)!)).toBe('allDay');
    expect(deviationOf(day('2025-05-31', 'uten regel'), base.byWeekday)).toBeNull();
  });

  it('setter ingen normal når ukedagen bare er sett én gang', () => {
    // Én observasjon er ingen normal: den ene dagen kan like gjerne *være*
    // avviket. Da er det riktigere å tie enn å gjette.
    const base = deriveBaseline([
      day('2025-05-10', '10:00-14:00', { matchedRule: { name: 'Enkeltdag', rule: '10.05.2025 ? ? 10:00-14:00' } }),
    ]);
    expect(base.byWeekday.has(6)).toBe(false);
  });

  it('gjør døgnåpent til normalen for tjenester uten regler i det hele tatt', () => {
    // En tjeneste ingen har satt opp er døgnåpen etter avtalen med backend.
    // Da er den døgnåpen hver dag, og skal aldri dukke opp som avvik.
    const mondays = ['2025-05-05', '2025-05-12', '2025-05-19', '2025-05-26'];
    const base = deriveBaseline(mondays.map((d) => day(d, 'uten regel')));
    expect(signatureKey(base.byWeekday.get(1)!)).toBe('allDay');
    expect(deviationOf(day('2025-06-02', 'uten regel'), base.byWeekday)).toBeNull();
  });
});

describe('deviationOf', () => {
  const baseline = deriveBaseline([
    day('2025-05-05', '08:00-15:30'),
    day('2025-05-12', '08:00-15:30'),
  ]).byWeekday;

  it('sier ingenting når dagen er som vanlig', () => {
    expect(deviationOf(day('2025-05-19', '08:00-15:30'), baseline)).toBeNull();
  });

  it('nevner bare den enden som er flyttet', () => {
    expect(deviationOf(day('2025-05-19', '08:00-12:00'), baseline)).toMatchObject({
      kind: 'shorter',
      summary: 'Stenger 12:00',
      normally: 'normalt 08:00–15:30',
    });
    expect(deviationOf(day('2025-05-19', '10:00-15:30'), baseline)).toMatchObject({
      kind: 'shorter',
      summary: 'Åpner 10:00',
    });
  });

  it('skiller lenger åpent fra kortere', () => {
    expect(deviationOf(day('2025-05-19', '08:00-20:00'), baseline)).toMatchObject({
      kind: 'longer',
      summary: 'Stenger 20:00',
    });
  });

  it('viser hele intervallet når begge ender er flyttet', () => {
    expect(deviationOf(day('2025-05-19', '10:00-20:00'), baseline)).toMatchObject({
      kind: 'moved',
      summary: 'Åpent 10:00–20:00',
    });
  });

  it('tar med helligdagsnavnet når dagen er stengt og rød', () => {
    // 9. juni 2025 er en mandag — normalen er 08:00–15:30, så stengt er et avvik.
    const pinsedag = deviationOf(
      day('2025-06-09', '00:00-00:00', { redDay: true }),
      baseline,
    );
    expect(pinsedag).toMatchObject({ kind: 'closed', summary: 'Stengt · Andre pinsedag' });
  });

  it('melder ekstra åpning når normalen er stengt', () => {
    const weekend = deriveBaseline([
      day('2025-05-03', '00:00-00:00'),
      day('2025-05-10', '00:00-00:00'),
    ]).byWeekday;
    expect(deviationOf(day('2025-05-17', '10:00-14:00'), weekend)).toMatchObject({
      kind: 'extra',
      summary: 'Åpent 10:00–14:00',
      normally: 'normalt stengt',
    });
  });

  it('melder ustabilitet selv når klokkeslettene er som vanlig', () => {
    const unstable = day('2025-05-19', '08:00-15:30', { unstableOpeningHours: true });
    expect(deviationOf(unstable, baseline)).toMatchObject({ kind: 'unstable', unstable: true });
  });

  it('tier når normalen for ukedagen er ukjent', () => {
    // Søndag finnes ikke i grunnlaget over. Uten sammenligningsgrunnlag finnes
    // det ikke noe avvik å påstå.
    expect(deviationOf(day('2025-05-18', '10:00-14:00'), baseline)).toBeNull();
  });
});

describe('åpningstider over midnatt', () => {
  const night = deriveBaseline([
    day('2025-05-05', '22:00-02:00'),
    day('2025-05-12', '22:00-02:00'),
  ]).byWeekday;

  it('melder stenging av en nattåpen tjeneste', () => {
    expect(deviationOf(day('2025-05-19', '00:00-00:00'), night)).toMatchObject({
      kind: 'closed',
      normally: 'normalt 22:00–02:00',
    });
  });

  it('regner tidligere stenging riktig over midnatt', () => {
    // 01:00 ligger *etter* 22:00, ikke 21 timer før. Uten normaliseringen av
    // sluttidspunktet ville dette blitt lest som lengre åpent.
    expect(deviationOf(day('2025-05-19', '22:00-01:00'), night)).toMatchObject({
      kind: 'shorter',
      summary: 'Stenger 01:00',
    });
    expect(deviationOf(day('2025-05-19', '22:00-03:00'), night)).toMatchObject({
      kind: 'longer',
    });
  });
});

describe('måneden som vises', () => {
  const SEASONAL = '??.07.???? ? 1-5 09:00-14:00';

  /** Mandag 30. juni (grunnregel) + hele juli på sesongregelen. */
  function julyWindow(): QueryResponse[] {
    const days = [day('2025-06-30', '08:00-15:30')];
    for (let d = 1; d <= 31; d++) {
      const date = `2025-07-${String(d).padStart(2, '0')}`;
      const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
      if (weekday === 0 || weekday === 6) continue;
      days.push(day(date, '09:00-14:00', { matchedRule: { name: 'Sommer', rule: SEASONAL } }));
    }
    return days;
  }

  it('lar ikke en kantdag fra forrige måned sette normalen', () => {
    // Mandag 30. juni er den eneste dagen i vinduet som treffer grunnregelen.
    // Uten avgrensningen ville alle julis mandager blitt meldt som avvik, mens
    // tirsdag til fredag var stille — samme tjeneste, samme sesongendring.
    const scoped = deriveBaseline(julyWindow(), '2025-07').byWeekday;
    expect(describeSignature(scoped.get(1)!)).toBe('09:00–14:00');
    expect(deviationOf(day('2025-07-07', '09:00-14:00'), scoped)).toBeNull();

    const unscoped = deriveBaseline(julyWindow()).byWeekday;
    expect(describeSignature(unscoped.get(1)!)).toBe('08:00–15:30');
  });

  it('teller ikke avvik på dager som ikke vises', () => {
    const withJune = buildCalendar(
      [
        {
          serviceId: 'a',
          serviceName: 'Alfa',
          team: 'T1',
          days: [...julyWindow(), day('2025-06-30', '00:00-00:00')],
        },
      ],
      '2025-07',
    );
    // 30. juni ligger i rutenettet for å gi normalen data, men tegnes ikke i
    // noen celle. Et tall brukeren ikke kan finne igjen er verre enn intet tall.
    expect(withJune.byDate.has('2025-06-30')).toBe(false);
    expect(withJune.total).toBe(0);
  });
});

describe('buildCalendar', () => {
  const normalDays = [
    ...NORMAL_WEEK,
    '2025-05-12',
    '2025-05-13',
    '2025-05-14',
    '2025-05-15',
  ].map((d) => day(d, '08:00-15:30'));

  it('samler avvik per dato og teller dem', () => {
    const calendar = buildCalendar([
      {
        serviceId: 'a',
        serviceName: 'Alfa',
        team: 'T1',
        days: [...normalDays, day('2025-05-19', '08:00-12:00')],
      },
      {
        serviceId: 'b',
        serviceName: 'Beta',
        team: 'T2',
        days: [...normalDays, day('2025-05-19', '00:00-00:00')],
      },
    ]);

    expect(calendar.total).toBe(2);
    expect(calendar.byDate.get('2025-05-19')).toHaveLength(2);
    // Stengt er mer alvorlig enn kortere åpent, og skal stå først.
    expect(calendar.byDate.get('2025-05-19')?.map((e) => e.serviceName)).toEqual(['Beta', 'Alfa']);
    expect(calendar.byDate.has('2025-05-12')).toBe(false);
  });

  it('melder ingen avvik for tjenester som mangler regler hele måneden', () => {
    const calendar = buildCalendar([
      { serviceId: 'a', serviceName: 'Alfa', team: 'T1', days: normalDays },
      {
        serviceId: 'c',
        serviceName: 'Gamma',
        team: 'T3',
        days: NORMAL_WEEK.map((d) => day(d, 'uten regel')),
      },
    ]);

    expect(calendar.total).toBe(0);
    expect(calendar.byDate.size).toBe(0);
  });

  it('lister kommende avvik kronologisk fra en gitt dato', () => {
    const calendar = buildCalendar([
      {
        serviceId: 'a',
        serviceName: 'Alfa',
        team: 'T1',
        days: [...normalDays, day('2025-05-19', '08:00-12:00'), day('2025-05-26', '00:00-00:00')],
      },
    ]);

    expect(upcoming(calendar, '2025-05-20').map((e) => e.date)).toEqual(['2025-05-26']);
    expect(upcoming(calendar, '2025-05-01').map((e) => e.date)).toEqual([
      '2025-05-19',
      '2025-05-26',
    ]);
  });
});
