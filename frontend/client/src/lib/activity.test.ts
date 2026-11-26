import { describe, expect, it } from 'vitest';
import type { Activity } from '../api/types';
import {
  activitiesOn,
  activityDateTimeInput,
  affectedServiceNames,
  formatActivityDateTime,
  formatActivityPeriod,
  type TimedActivity,
} from './activity';

describe('activity date-time formatting', () => {
  it('should format a time on either side of the Oslo daylight-saving transition', () => {
    expect(activityDateTimeInput('2025-03-30T00:30:00Z')).toBe('2025-03-30T01:30');
    expect(activityDateTimeInput('2025-03-30T01:30:00Z')).toBe('2025-03-30T03:30');
  });

  it('should keep Oslo midnight on the correct calendar date', () => {
    expect(activityDateTimeInput('2025-03-30T22:00:00Z')).toBe('2025-03-31T00:00');
    expect(activityDateTimeInput(null)).toBe('');
  });

  it('should format a date and time in Norwegian', () => {
    expect(formatActivityDateTime('2025-03-30T01:30:00Z')).toContain('30. mars 2025');
    expect(formatActivityDateTime('2025-03-30T01:30:00Z')).toContain('03:30');
  });
});

function activity(start: string, end: string, extra: Partial<Activity> = {}): TimedActivity {
  return {
    id: `${start}-${end}`,
    serviceIds: [],
    header: 'Vedlikehold',
    message: '',
    start,
    end,
    onlyShowForNavEmployees: false,
    ongoing: false,
    ...extra,
  } as TimedActivity;
}

describe('activitiesOn', () => {
  it('should place the activity on the Oslo day, not the UTC day', () => {
    const a = activity('2025-05-13T00:30:00+02:00', '2025-05-13T02:00:00+02:00');
    expect(activitiesOn([a], '2025-05-13')).toEqual([a]);
    expect(activitiesOn([a], '2025-05-12')).toEqual([]);
  });

  it('should include every day a multi-day activity touches, with an exclusive end', () => {
    const a = activity('2025-05-12T22:00:00+02:00', '2025-05-14T00:00:00+02:00');
    expect(
      ['2025-05-11', '2025-05-12', '2025-05-13', '2025-05-14'].map(
        (d) => activitiesOn([a], d).length,
      ),
    ).toEqual([0, 1, 1, 0]);
  });

  it('should skip activities without a time span', () => {
    expect(activitiesOn([{ ...activity('', ''), start: null, end: null }], '2025-05-13')).toEqual([]);
  });
});

describe('formatActivityPeriod', () => {
  it('should show times only within one day, and midnight as 24:00', () => {
    expect(formatActivityPeriod(activity('2025-05-12T18:00:00+02:00', '2025-05-12T22:00:00+02:00')))
      .toBe('18:00–22:00');
    expect(formatActivityPeriod(activity('2025-05-12T18:00:00+02:00', '2025-05-13T00:00:00+02:00')))
      .toBe('18:00–24:00');
  });

  it('should show dates when the activity spans several days', () => {
    expect(formatActivityPeriod(activity('2025-05-12T22:00:00+02:00', '2025-05-13T06:00:00+02:00')))
      .toBe('12. mai 22:00 – 13. mai 06:00');
  });
});

describe('affectedServiceNames', () => {
  it('should merge catalog names and external names without duplicates', () => {
    const a = activity('2025-05-12T08:00:00+02:00', '2025-05-12T09:00:00+02:00', {
      serviceIds: ['s1', 'missing'],
      serviceNames: ['ID-porten', 'dagpenger'],
    });
    const byId = new Map([['s1', { name: 'Dagpenger' }]]);
    expect(affectedServiceNames(a, byId)).toEqual(['Dagpenger', 'Ukjent tjeneste', 'ID-porten']);
  });
});
