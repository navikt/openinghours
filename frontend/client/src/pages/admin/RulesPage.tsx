import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  BodyShort,
  Button,
  HStack,
  Heading,
  Search,
  Select,
  Table,
  Tag,
  VStack,
} from '@navikt/ds-react';
import type { OhGroup, Rule } from '../../api/types';
import { useGroups, useRules } from '../../hooks/admin';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { ErrorState, EmptyState } from '../../components/common/ErrorState';
import { formatRule } from '../../lib/rule';
import { validateRule } from '../../lib/validate';
import './RulesPage.css';
import { AppLink } from '../../components/common/AppLink';

type Flag = 'alle' | 'ansatte' | 'ubrukt' | 'ugyldig';

/** Hvor mange grupper bruker hver regel? Gruppene bærer koblingen selv. */
function usageCount(groups: OhGroup[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const group of groups) {
    for (const id of group.ruleGroupIds ?? []) {
      counts.set(id, (counts.get(id) ?? 0) + 1);
    }
  }
  return counts;
}

export function RulesPage() {
  const rules = useRules();
  const groups = useGroups();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const flag = (params.get('flagg') as Flag) ?? 'alle';

  const counts = useMemo(() => usageCount(groups.data ?? []), [groups.data]);

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (rules.data ?? []).filter((rule) => {
      if (needle && !`${rule.name} ${rule.rule}`.toLowerCase().includes(needle)) return false;
      if (flag === 'ansatte') return rule.onlyShowForNavEmployees;
      if (flag === 'ubrukt') return (counts.get(rule.id) ?? 0) === 0;
      if (flag === 'ugyldig') return validateRule(rule.rule) !== null;
      return true;
    });
  }, [rules.data, search, flag, counts]);

  if (rules.isPending || groups.isPending) return <DelayedLoader />;
  if (rules.isError) {
    return <ErrorState message="Vi klarte ikke å hente reglene." onRetry={() => rules.refetch()} />;
  }

  return (
    <VStack gap="5">
      <HStack justify="space-between" align="end" wrap gap="4">
        <div>
          <Heading level="1" size="large">
            Regler
          </Heading>
          <BodyShort textColor="subtle">
            {rules.data.length} {rules.data.length === 1 ? 'regel' : 'regler'} · en regel kan brukes
            i flere grupper
          </BodyShort>
        </div>
        <Button as={Link} to="/admin/regler/ny">
          Ny regel
        </Button>
      </HStack>

      <HStack gap="4" align="end" wrap>
        <Search
          label="Søk i navn og uttrykk"
          size="small"
          variant="simple"
          value={search}
          onChange={setSearch}
          onClear={() => setSearch('')}
        />
        <Select
          label="Flagg"
          size="small"
          value={flag}
          onChange={(e) => {
            const next = new URLSearchParams(params);
            if (e.target.value === 'alle') next.delete('flagg');
            else next.set('flagg', e.target.value);
            setParams(next, { replace: true });
          }}
        >
          <option value="alle">Alle regler</option>
          <option value="ansatte">Kun for Nav-ansatte</option>
          <option value="ubrukt">Ikke i bruk</option>
          <option value="ugyldig">Uttrykket kan ikke tolkes</option>
        </Select>
      </HStack>

      {visible.length === 0 ? (
        <EmptyState
          title="Ingen regler traff filteret"
          description="Prøv et annet søkeord, eller velg «Alle regler» i flaggfilteret."
        />
      ) : (
        <Table size="small" className="oh-rules">
          <Table.Header>
            <Table.Row>
              <Table.HeaderCell scope="col">Navn</Table.HeaderCell>
              <Table.HeaderCell scope="col">Regeluttrykk</Table.HeaderCell>
              <Table.HeaderCell scope="col">Flagg</Table.HeaderCell>
              <Table.HeaderCell scope="col">Brukt i</Table.HeaderCell>
              <Table.HeaderCell scope="col">
                <span className="oh-sr-only">Handlinger</span>
              </Table.HeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {visible.map((rule) => (
              <RuleRow key={rule.id} rule={rule} usedIn={counts.get(rule.id) ?? 0} />
            ))}
          </Table.Body>
        </Table>
      )}
    </VStack>
  );
}

function RuleRow({ rule, usedIn }: { rule: Rule; usedIn: number }) {
  const error = validateRule(rule.rule);

  return (
    <Table.Row>
      <Table.DataCell>
        <AppLink to={`/admin/regler/${rule.id}`}>{rule.name}</AppLink>
      </Table.DataCell>
      <Table.DataCell>
        <code className="oh-rules__expr">{rule.rule}</code>
        <span className="oh-rules__sentence">{error ? error.message : formatRule(rule.rule)}</span>
      </Table.DataCell>
      <Table.DataCell>
        <HStack gap="1" wrap>
          {/* Rød dag settes aldri av skrive-endepunktene, men eldre rader kan ha
              flagget satt i databasen. Vi viser det når det finnes. */}
          {rule.redDay && (
            <Tag size="small" variant="alt1">
              Rød dag
            </Tag>
          )}
          {rule.onlyShowForNavEmployees && (
            <Tag size="small" variant="alt3">
              Kun for Nav-ansatte
            </Tag>
          )}
          {usedIn === 0 && (
            <Tag size="small" variant="neutral">
              Ikke i bruk
            </Tag>
          )}
          {error && (
            <Tag size="small" variant="error">
              Ugyldig uttrykk
            </Tag>
          )}
        </HStack>
      </Table.DataCell>
      <Table.DataCell>
        {usedIn === 0 ? (
          <span className="oh-warning-text">Ikke i bruk</span>
        ) : (
          `${usedIn} ${usedIn === 1 ? 'gruppe' : 'grupper'}`
        )}
      </Table.DataCell>
      <Table.DataCell>
        <VStack gap="1">
          <AppLink to={`/admin/regler/${rule.id}`}>Rediger</AppLink>
          <AppLink to={`/admin/regler/${rule.id}?slett=1`}>Slett</AppLink>
        </VStack>
      </Table.DataCell>
    </Table.Row>
  );
}
