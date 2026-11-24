import { describe, expect, it } from 'vitest';
import { activityDateTimeInput, formatActivityDateTime } from './activity';

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
