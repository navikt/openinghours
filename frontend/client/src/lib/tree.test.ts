import { describe, expect, it } from 'vitest';
import type { OhGroup, Rule } from '../api/types';
import { buildRegistry, buildTree, moveItem, unreachableMembers, wouldCycle } from './tree';

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

describe('buildTree', () => {
  it('nummererer medlemmene fortløpende', () => {
    const registry = buildRegistry(
      [group('g1', 'Rot', ['r1', 'r2'])],
      [rule('r1', 'Først'), rule('r2', 'Så')],
    );
    expect(buildTree('g1', registry).map((n) => [n.priority, n.name])).toEqual([
      ['1', 'Først'],
      ['2', 'Så'],
    ]);
  });

  it('gir undergruppens medlemmer sammensatt prioritet', () => {
    const registry = buildRegistry(
      [group('g1', 'Rot', ['r1', 'g2']), group('g2', 'Under', ['r2', 'r3'])],
      [rule('r1', 'A'), rule('r2', 'B'), rule('r3', 'C')],
    );
    const tree = buildTree('g1', registry);
    expect(tree[1].kind).toBe('group');
    expect(tree[1].memberCount).toBe(2);
    expect(tree[1].children.map((n) => n.priority)).toEqual(['2.1', '2.2']);
  });

  it('stopper på tredje nivå og merker raden som avkortet', () => {
    const registry = buildRegistry(
      [
        group('g1', 'Nivå 1', ['g2']),
        group('g2', 'Nivå 2', ['g3']),
        group('g3', 'Nivå 3', ['g4']),
        group('g4', 'Nivå 4', ['r1']),
      ],
      [rule('r1', 'Dyp')],
    );
    const tree = buildTree('g1', registry);
    const nivå3 = tree[0].children[0].children[0];
    expect(nivå3.name).toBe('Nivå 4');
    expect(nivå3.truncated).toBe(true);
    expect(nivå3.children).toEqual([]);
  });

  it('markerer id-er som verken er regel eller gruppe', () => {
    const registry = buildRegistry([group('g1', 'Rot', ['ukjent'])], []);
    expect(buildTree('g1', registry)[0].missing).toBe(true);
  });

  it('henger ikke på en sirkel i dataene', () => {
    const registry = buildRegistry(
      [group('g1', 'A', ['g2']), group('g2', 'B', ['g1'])],
      [],
    );
    expect(() => buildTree('g1', registry)).not.toThrow();
  });

  it('takler at ruleGroupIds er null', () => {
    const registry = buildRegistry([{ id: 'g1', name: 'Tom', ruleGroupIds: null }], []);
    expect(buildTree('g1', registry)).toEqual([]);
  });
});

describe('moveItem', () => {
  it('flytter oppover og nedover', () => {
    expect(moveItem(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b']);
    expect(moveItem(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
  });

  it('lar listen være i fred utenfor grensene', () => {
    const list = ['a', 'b'];
    expect(moveItem(list, 0, -1)).toBe(list);
    expect(moveItem(list, 0, 5)).toBe(list);
    expect(moveItem(list, 1, 1)).toBe(list);
  });
});

describe('wouldCycle', () => {
  const registry = buildRegistry(
    [group('g1', 'A', ['g2']), group('g2', 'B', ['g3']), group('g3', 'C'), group('g4', 'D')],
    [],
  );

  it('en gruppe kan ikke inneholde seg selv', () => {
    expect(wouldCycle('g1', 'g1', registry)).toBe(true);
  });

  it('oppdager indirekte sirkel', () => {
    expect(wouldCycle('g3', 'g1', registry)).toBe(true);
  });

  it('godtar en gruppe som ikke er i slekt', () => {
    expect(wouldCycle('g1', 'g4', registry)).toBe(false);
  });
});

describe('unreachableMembers', () => {
  const registry = (members: string[], rules: Rule[]) =>
    buildRegistry([group('g1', 'Rot', members)], rules);

  it('merker alt etter en regel som dekker alle datoer', () => {
    const tree = buildTree(
      'g1',
      registry(
        ['r1', 'r2', 'r3'],
        [
          rule('r1', 'Spesifikk', '17.05.???? ? ? 00:00-00:00'),
          rule('r2', 'Alltid', '??.??.???? ? ? 08:00-15:30'),
          rule('r3', 'Aldri', '??.??.???? ? 1-5 09:00-16:00'),
        ],
      ),
    );
    expect([...unreachableMembers(tree)]).toEqual(['r3']);
  });

  it('advarer ikke når ingen regel dekker alt', () => {
    const tree = buildTree(
      'g1',
      registry(
        ['r1', 'r2'],
        [rule('r1', 'Hverdag'), rule('r2', 'Helg', '??.??.???? ? 6,7 00:00-00:00')],
      ),
    );
    expect(unreachableMembers(tree).size).toBe(0);
  });
});
