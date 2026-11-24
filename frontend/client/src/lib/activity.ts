import { TIMEZONE } from './date';

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
