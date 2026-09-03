import { describe, expect, it } from 'vitest';
import type { OhGroup, Rule, Service } from '../api/types';
import {
  emptyGroups,
  findIssues,
  invalidRules,
  summarize,
  unusedGroups,
  unusedRules,
} from './health';

const rule = (id: string, name: string, expr = '??.??.???? ? 1-5 08:00-15:30'): Rule => ({
  id,
  name,
  rule: expr,
  header: null,
  text: null,
  onlyShowForNavEmployees: false,
  redDay: false,
  unstableOpeningHours: false,
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: null,
});

const group = (id: string, name: string, members: string[] = []): OhGroup => ({
  id,
  name,
  ruleGroupIds: members,
});

const service = (id: string, name: string): Service => ({
  id,
  name,
  type: 'TJENESTE',
  team: 'navdig',
  monitorlink: null,
  logglink: null,
  description: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: null,
});

describe('unusedRules', () => {
  it('finner regler som ikke ligger i noen gruppe', () => {
    const rules = [rule('r1', 'Brukt'), rule('r2', 'Ubrukt')];
    expect(unusedRules([group('g1', 'Gruppe', ['r1'])], rules).map((r) => r.id)).toEqual(['r2']);
  });

  it('takler at ruleGroupIds er null', () => {
    expect(unusedRules([{ id: 'g1', name: 'G', ruleGroupIds: null }], [rule('r1', 'A')])).toHaveLength(
      1,
    );
  });
});

describe('unusedGroups', () => {
  it('regner en gruppe som brukt når en tjeneste peker på den', () => {
    const groups = [group('g1', 'A'), group('g2', 'B')];
    expect(unusedGroups(groups, new Set(['g1'])).map((g) => g.id)).toEqual(['g2']);
  });

  it('regner en undergruppe som brukt', () => {
    const groups = [group('g1', 'Forelder', ['g2']), group('g2', 'Barn', ['r1'])];
    expect(unusedGroups(groups, new Set(['g1']))).toHaveLength(0);
  });
});

describe('emptyGroups', () => {
  it('finner grupper uten medlemmer', () => {
    expect(emptyGroups([group('g1', 'Tom'), group('g2', 'Full', ['r1'])]).map((g) => g.id)).toEqual([
      'g1',
    ]);
  });

  it('regner null som tom', () => {
    expect(emptyGroups([{ id: 'g1', name: 'G', ruleGroupIds: null }])).toHaveLength(1);
  });
});

describe('invalidRules', () => {
  it('plukker ut uttrykk validatoren avviser', () => {
    const rules = [rule('r1', 'Ok'), rule('r2', 'Ødelagt', '??.??.???? ? man-fre 08:00-15:30')];
    expect(invalidRules(rules).map((r) => r.id)).toEqual(['r2']);
  });
});

describe('findIssues', () => {
  const base = {
    services: [service('s1', 'Dagpenger')],
    groups: [group('g1', 'Standard', ['r1'])],
    rules: [rule('r1', 'Ordinær')],
    linkedServiceIds: new Set(['s1']),
  };

  it('gir ingen avvik når alt henger sammen', () => {
    expect(findIssues(base)).toEqual([]);
    expect(summarize([])).toBeNull();
  });

  it('melder fra om tjenester uten gruppe', () => {
    const issues = findIssues({ ...base, linkedServiceIds: new Set() });
    expect(issues[0].id).toBe('tjenester-uten-gruppe');
    expect(issues[0].title).toContain('1 tjeneste mangler');
    expect(issues[0].description).toContain('Dagpenger');
  });

  it('bøyer flertall riktig', () => {
    const issues = findIssues({
      ...base,
      services: [service('s1', 'A'), service('s2', 'B')],
      linkedServiceIds: new Set(),
    });
    expect(issues[0].title).toContain('2 tjenester mangler');
  });

  it('forkorter lange lister', () => {
    const services = ['A', 'B', 'C', 'D'].map((n, i) => service(`s${i}`, n));
    const issues = findIssues({ ...base, services, linkedServiceIds: new Set() });
    expect(issues[0].description).toContain('med flere');
  });

  it('setter advarsler før informasjon', () => {
    const issues = findIssues({
      ...base,
      groups: [group('g1', 'Tom')],
      rules: [rule('r1', 'Ubrukt')],
    });
    expect(issues.map((i) => i.severity)).toEqual(['warning', 'info']);
  });

  it('oppsummerer bare advarslene', () => {
    const issues = findIssues({
      ...base,
      groups: [group('g1', 'Tom')],
      rules: [rule('r1', 'Ubrukt')],
    });
    const summary = summarize(issues);
    expect(summary).toContain('1 gruppe er tom');
    expect(summary).not.toContain('ikke i bruk');
  });
});
