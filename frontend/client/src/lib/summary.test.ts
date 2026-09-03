import { describe, expect, it } from 'vitest';
import type { DailyCacheResponse, QueryResponse } from '../api/types';
import {
  attentionItems,
  bucketOf,
  countLabel,
  dailyToQuery,
  headline,
  presentBuckets,
  sortEntries,
  summarize,
  toEntry,
  type ServiceDay,
} from './summary';
import { deriveStatus } from './status';

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

function item(name: string, overrides: Partial<QueryResponse> = {}): ServiceDay {
  return {
    serviceId: name,
    serviceName: name,
    team: 'Team test',
    day: day(overrides),
  };
}

describe('bucketOf', () => {
  it('legger manglende svar i samme bøtte som manglende regel', () => {
    expect(bucketOf(null)).toBe('missing');
    expect(bucketOf(deriveStatus(day({ warningMessage: 'ingen regel' })))).toBe('missing');
  });

  it('lar ustabil gå foran åpen — det er avviket som teller', () => {
    expect(bucketOf(deriveStatus(day({ unstableOpeningHours: true })))).toBe('unstable');
  });

  it('teller rød dag som stengt', () => {
    expect(bucketOf(deriveStatus(day({ redDay: true, openingTime: '00:00', closingTime: '00:00' })))).toBe(
      'closed',
    );
  });

  it('teller døgnåpen som åpen', () => {
    expect(bucketOf(deriveStatus(day({ openingTime: '00:00', closingTime: '23:59' })))).toBe('open');
  });

  it('skiller «åpen i dag» fra «åpen nå» når klokkeslettet er kjent', () => {
    const open = deriveStatus(day());
    expect(bucketOf(open, 10 * 60)).toBe('open');
    expect(bucketOf(open, 20 * 60)).toBe('closed');
    expect(bucketOf(open, 7 * 60)).toBe('closed');
    // Uten klokkeslett — som for en framtidig dato — er dagen åpen.
    expect(bucketOf(open)).toBe('open');
  });

  it('holder døgnåpne tjenester åpne uansett klokkeslett', () => {
    const allDay = deriveStatus(day({ openingTime: '00:00', closingTime: '23:59' }));
    expect(bucketOf(allDay, 3 * 60)).toBe('open');
  });
});

describe('sortEntries', () => {
  it('setter avvik øverst og sorterer alfabetisk innenfor hver bøtte', () => {
    const entries = [
      item('Bravo'),
      item('Alfa'),
      item('Charlie', { warningMessage: 'ingen regel' }),
      item('Delta', { unstableOpeningHours: true }),
      item('Echo', { openingTime: '00:00', closingTime: '00:00' }),
    ].map((i) => toEntry(i));

    expect(sortEntries(entries).map((e) => e.serviceName)).toEqual([
      'Charlie',
      'Delta',
      'Echo',
      'Alfa',
      'Bravo',
    ]);
  });
});

describe('summarize', () => {
  it('teller hver tjeneste i nøyaktig én bøtte', () => {
    const summary = summarize('2026-05-12', [
      item('Alfa'),
      item('Bravo'),
      item('Charlie', { unstableOpeningHours: true }),
      item('Delta', { openingTime: '00:00', closingTime: '00:00' }),
      item('Echo', { warningMessage: 'ingen regel' }),
    ]);

    expect(summary.counts).toEqual({ open: 2, unstable: 1, closed: 1, missing: 1 });
    expect(summary.total).toBe(5);
  });

  it('flagger dagen som rød når de fleste tjenestene har rød dag', () => {
    const summary = summarize('2026-05-17', [
      item('Alfa', { date: '2026-05-17', redDay: true, openingTime: '00:00', closingTime: '00:00' }),
      item('Bravo', { date: '2026-05-17', redDay: true, openingTime: '00:00', closingTime: '00:00' }),
      item('Charlie', { date: '2026-05-17' }),
    ]);

    expect(summary.redDay).toBe(true);
    expect(summary.holiday).toBe('Grunnlovsdagen');
  });

  it('flagger ikke dagen som rød når bare én tjeneste har det', () => {
    const summary = summarize('2026-05-12', [
      item('Alfa', { redDay: true, openingTime: '00:00', closingTime: '00:00' }),
      item('Bravo'),
      item('Charlie'),
    ]);

    expect(summary.redDay).toBe(false);
    expect(summary.holiday).toBeNull();
  });

  it('teller tjenester uten svar som uten åpningstider', () => {
    const summary = summarize('2026-05-12', [{ ...item('Alfa'), day: null }]);
    expect(summary.counts.missing).toBe(1);
  });
});

describe('countLabel', () => {
  it('bøyer entall og flertall', () => {
    expect(countLabel('open', 1)).toBe('1 åpen');
    expect(countLabel('open', 41)).toBe('41 åpne');
    expect(countLabel('unstable', 1)).toBe('1 ustabil');
    expect(countLabel('closed', 3)).toBe('3 stengte');
    expect(countLabel('missing', 2)).toBe('2 uten åpningstider');
  });
});

describe('presentBuckets', () => {
  it('utelater tomme bøtter og beholder prioritert rekkefølge', () => {
    expect(presentBuckets({ open: 41, unstable: 0, closed: 3, missing: 2 })).toEqual([
      'missing',
      'closed',
      'open',
    ]);
  });
});

describe('headline', () => {
  it('sier «nå» kun for dagens dato', () => {
    expect(headline({ open: 41, unstable: 1, closed: 3, missing: 2 }, 47, true)).toBe(
      '41 av 47 tjenester er åpne nå',
    );
    expect(headline({ open: 41, unstable: 1, closed: 3, missing: 2 }, 47, false)).toBe(
      '41 av 47 tjenester er åpne',
    );
  });
});

describe('attentionItems', () => {
  it('utelater åpne tjenester og begrenser antallet', () => {
    const entries = sortEntries(
      [
        item('Alfa'),
        item('Bravo', { warningMessage: 'ingen regel' }),
        item('Charlie', { unstableOpeningHours: true }),
      ].map((i) => toEntry(i)),
    );

    const items = attentionItems(entries, 5);
    expect(items.map((i) => i.serviceName)).toEqual(['Bravo', 'Charlie']);
    expect(items[0].detail).toBe('mangler åpningstider');
    expect(items[1].detail).toBe('er ustabil 08:00–15:30');
  });

  it('navngir helligdagen når tjenesten er stengt på rød dag', () => {
    const entries = [
      toEntry(item('Alfa', { date: '2026-05-17', redDay: true, openingTime: '00:00', closingTime: '00:00' })),
    ];
    expect(attentionItems(entries)[0].detail).toBe('stengt · Grunnlovsdagen');
  });

  it('sier når en tjeneste åpner eller stengte, framfor «stengt hele dagen»', () => {
    const before = summarize('2026-05-12', [item('Alfa')], 7 * 60);
    expect(attentionItems(before.entries, 5, 7 * 60)[0].detail).toBe('åpner 08:00');

    const after = summarize('2026-05-12', [item('Alfa')], 20 * 60);
    expect(attentionItems(after.entries, 5, 20 * 60)[0].detail).toBe('stengte 15:30');
  });
});

describe('dailyToQuery', () => {
  const base: DailyCacheResponse = {
    serviceId: 's1',
    serviceName: 'Dagpenger',
    isOpen: true,
    openingHours: '08:00-15:30',
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    ruleName: 'Ordinær åpningstid',
    rule: '??.??.???? ? 1-5 08:00-15:30',
  };

  it('deler åpningstiden i start og slutt', () => {
    const result = dailyToQuery(base, '2026-05-12');
    expect(result.openingTime).toBe('08:00');
    expect(result.closingTime).toBe('15:30');
    expect(result.matchedRule).toEqual({ name: 'Ordinær åpningstid', rule: '??.??.???? ? 1-5 08:00-15:30' });
  });

  it('gir samme status som query-endepunktet ville gitt', () => {
    expect(deriveStatus(dailyToQuery(base, '2026-05-12')).label).toBe('Åpen 08:00–15:30');
  });

  it('behandler manglende åpningstid som at ingen regel traff', () => {
    const result = dailyToQuery({ ...base, openingHours: null, ruleName: null, rule: null }, '2026-05-12');
    expect(deriveStatus(result).kind).toBe('warning');
    expect(result.matchedRule).toBeUndefined();
  });

  /*
   * Backend sender ikke `null` her i praksis: en tjeneste uten gruppe — eller uten
   * regel som treffer — får `DEFAULT_DISPLAY_DATA`, som er døgnåpent. Uten denne
   * gjenkjenningen ville et manglende oppsett sett ut som en frisk, døgnåpen
   * tjeneste akkurat i dag, og først dukket opp som avvik i morgen.
   */
  it('gjenkjenner backendens standardregel som manglende oppsett', () => {
    const result = dailyToQuery(
      {
        ...base,
        openingHours: '00:00-23:59',
        ruleName: 'No Rules stated',
        rule: '??.??.???? ? ? 00:00-23:59',
        displayHeader: 'Default regel',
        displayText: 'Åpent - ingen gjeldende dato regler',
      },
      '2026-05-12',
    );

    expect(deriveStatus(result).kind).toBe('warning');
    expect(bucketOf(deriveStatus(result))).toBe('missing');
    expect(result.matchedRule).toBeUndefined();
  });

  it('lar en ekte døgnåpen regel være åpen', () => {
    const result = dailyToQuery(
      { ...base, openingHours: '00:00-23:59', ruleName: 'Døgnåpen komponent', rule: '??.??.???? ? ? 00:00-23:59' },
      '2026-05-12',
    );
    expect(bucketOf(deriveStatus(result), 3 * 60)).toBe('open');
  });
});
