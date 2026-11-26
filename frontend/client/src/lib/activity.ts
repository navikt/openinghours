import type { Activity, Service } from '../api/types';
import { TIMEZONE, todayIso } from './date';

const dateTimeParts = new Intl.DateTimeFormat('nb-NO', {
  timeZone: TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function formatActivityDateTime(value: string): string {
  return new Intl.DateTimeFormat('nb-NO', {
    timeZone: TIMEZONE,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(value));
}

export function activityDateTimeInput(value: string | null): string {
  if (value === null) return '';

  const parts = dateTimeParts.formatToParts(new Date(value));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value ?? '';

  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
}

const timeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const dayFormat = new Intl.DateTimeFormat('nb-NO', {
  timeZone: TIMEZONE,
  day: 'numeric',
  month: 'short',
});

/** Aktivitet med kjent tidsrom — de eneste som kan plasseres på en kalenderdag. */
export type TimedActivity = Activity & { start: string; end: string };

export function isTimed(activity: Activity): activity is TimedActivity {
  return activity.start !== null && activity.end !== null;
}

/** Første og siste kalenderdag i Oslo aktiviteten berører. Slutt er eksklusiv. */
export function activityDays(activity: TimedActivity): { first: string; last: string } {
  const startMs = new Date(activity.start).getTime();
  const endMs = new Date(activity.end).getTime();
  return {
    first: todayIso(new Date(startMs)),
    last: todayIso(new Date(Math.max(endMs - 1, startMs))),
  };
}

/** Aktivitetene som berører datoen, de tidligste først. */
export function activitiesOn(activities: readonly Activity[], date: string): TimedActivity[] {
  return activities
    .filter(isTimed)
    .filter((activity) => {
      const { first, last } = activityDays(activity);
      return first <= date && date <= last;
    })
    .sort(
      (a, b) =>
        new Date(a.start).getTime() - new Date(b.start).getTime() ||
        a.header.localeCompare(b.header, 'nb'),
    );
}

/**
 * «18:00–22:00» når aktiviteten holder seg innenfor én dag, ellers med datoer:
 * «12. mai 18:00 – 13. mai 06:00». Slutt ved midnatt vises som 24:00 samme dag.
 */
export function formatActivityPeriod(activity: TimedActivity): string {
  const { first, last } = activityDays(activity);
  const start = timeFormat.format(new Date(activity.start));
  let end = timeFormat.format(new Date(activity.end));
  if (end === '00:00' && todayIso(new Date(activity.end)) !== last) end = '24:00';
  if (first === last) return `${start}–${end}`;
  const startDay = dayFormat.format(new Date(activity.start));
  const endDay = dayFormat.format(new Date(`${last}T12:00:00Z`));
  return `${startDay} ${start} – ${endDay} ${end}`;
}

/** Navnene på berørte tjenester: katalogtjenester først, deretter de utenfor katalogen. */
export function affectedServiceNames(
  activity: Activity,
  servicesById: ReadonlyMap<string, Pick<Service, 'name'>>,
): string[] {
  const names = activity.serviceIds.map((id) => servicesById.get(id)?.name ?? 'Ukjent tjeneste');
  const seen = new Set(names.map((name) => name.toLocaleLowerCase('nb')));
  for (const name of activity.serviceNames ?? []) {
    const key = name.toLocaleLowerCase('nb');
    if (!seen.has(key)) {
      seen.add(key);
      names.push(name);
    }
  }
  return names;
}

/** Navn utenfor katalogen — de som ikke allerede dekkes av en valgt katalogtjeneste. */
export function extraServiceNames(
  activity: Activity,
  servicesById: ReadonlyMap<string, Pick<Service, 'name'>>,
): string[] {
  const catalog = new Set(
    activity.serviceIds
      .map((id) => servicesById.get(id)?.name.toLocaleLowerCase('nb'))
      .filter((name): name is string => name !== undefined),
  );
  return affectedServiceNames({ ...activity, serviceIds: [] }, servicesById).filter(
    (name) => !catalog.has(name.toLocaleLowerCase('nb')),
  );
}
