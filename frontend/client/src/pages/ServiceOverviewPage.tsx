import { useEffect, useMemo, useState } from 'react';
import {
  BodyLong,
  BodyShort,
  Heading,
  Link,
  Search,
  Select,
  Skeleton,
  Table,
  Tag,
} from '@navikt/ds-react';
import { ArrowLeftIcon, ExternalLinkIcon } from '@navikt/aksel-icons';
import { useSearchParams } from 'react-router-dom';
import type { ServiceType } from '../api/types';
import { useDailyStatus, useServices, useSession } from '../hooks/queries';
import { hasNoRule } from '../lib/daily';
import { formatHours, HOURS_ALWAYS_OPEN, HOURS_CLOSED } from '../lib/rule';
import { AppLink } from '../components/common/AppLink';
import { StatusBadge, UnstableMark } from '../components/calendar/StatusBadge';
import { EmptyState, ErrorState } from '../components/common/ErrorState';
import { DelayedLoader } from '../components/common/DelayedLoader';
import { useNow } from '../hooks/useNow';
import { useDebounced } from '../hooks/useDebounced';
import './ServiceOverviewPage.css';

export function ServiceOverviewPage() {
  const [params, setParams] = useSearchParams();
  const services = useServices();
  const daily = useDailyStatus();
  const session = useSession();
  const { now } = useNow();
  const [sortBy, setSortBy] = useState<'name' | 'team'>('name');

  const search = params.get('sok') ?? '';
  const team = params.get('team') ?? '';
  const type = params.get('type') ?? '';
  const loggedIn = session.data?.loggedIn ?? false;

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  /*
   * Feltet eier sin egen verdi og skriver til URL-en først når brukeren tar en
   * pause. Uten dette utløste hvert tastetrykk en ruternavigasjon, som gjør
   * skrivingen hakkete i lange lister.
   */
  const [query, setQuery] = useState(search);
  const debouncedQuery = useDebounced(query, 250);

  useEffect(() => {
    if (debouncedQuery !== search) setParam('sok', debouncedQuery);
  }, [debouncedQuery]);

  // Trykker brukeren tilbake, eller deles en filtrert lenke, må feltet følge URL-en.
  useEffect(() => {
    setQuery((current) => (current === search ? current : search));
  }, [search]);

  const teams = useMemo(
    () => [...new Set((services.data ?? []).map((s) => s.team))].sort((a, b) => a.localeCompare(b, 'nb')),
    [services.data],
  );

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (services.data ?? [])
      .filter((s) => (needle ? s.name.toLowerCase().includes(needle) : true))
      .filter((s) => (team ? s.team === team : true))
      .filter((s) => (type ? s.type === (type as ServiceType) : true))
      .sort((a, b) => a[sortBy].localeCompare(b[sortBy], 'nb'));
  }, [services.data, search, team, type, sortBy]);

  const timestamp = new Intl.DateTimeFormat('nb-NO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Europe/Oslo',
  }).format(now);

  if (services.isError) {
    return (
      <ErrorState message="Vi klarte ikke å hente tjenestelisten." onRetry={() => services.refetch()} />
    );
  }

  return (
    <div className="oh-overview">
      <AppLink to="/" className="oh-back">
        <ArrowLeftIcon aria-hidden /> Tilbake til oversikten
      </AppLink>

      <div>
        <Heading level="1" size="xlarge" spacing>
          Alle tjenester
        </Heading>
        <BodyLong size="large">
          Se om en tjeneste er åpen nå, og når den er åpen resten av måneden.
        </BodyLong>
      </div>

      <div className="oh-filters">
        <Search
          label="Søk etter tjeneste"
          placeholder="F.eks. dagpenger"
          size="small"
          value={query}
          onChange={(value) => setQuery(value)}
          onClear={() => setQuery('')}
          variant="simple"
          className="oh-filters__search"
        />
        <Select
          label="Team"
          size="small"
          value={team}
          onChange={(e) => setParam('team', e.target.value)}
        >
          <option value="">Alle team</option>
          {teams.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </Select>
        <Select
          label="Type"
          size="small"
          value={type}
          onChange={(e) => setParam('type', e.target.value)}
        >
          <option value="">Alle typer</option>
          <option value="TJENESTE">Tjeneste</option>
          <option value="KOMPONENT">Komponent</option>
        </Select>
      </div>

      <div className="oh-overview__count">
        <BodyShort size="small" aria-live="polite">
          Viser {filtered.length} av {services.data?.length ?? 0} tjenester · {timestamp}
        </BodyShort>
        <BodyShort size="small" textColor="subtle">
          Sortert etter {sortBy === 'team' ? 'team' : 'navn'}
        </BodyShort>
      </div>

      {services.isPending && <DelayedLoader />}

      {!services.isPending && filtered.length === 0 && (
        <EmptyState
          title="Ingen tjenester matcher søket"
          description="Prøv et kortere søkeord, eller nullstill filtrene for team og type."
          actionLabel="Nullstill filtrene"
          onAction={() => setParams(new URLSearchParams(), { replace: true })}
        />
      )}

      {filtered.length > 0 && (
        <Table
          size="small"
          sort={{ orderBy: sortBy, direction: 'ascending' }}
          onSortChange={(key) => setSortBy(key === 'team' ? 'team' : 'name')}
        >
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeader sortKey="name" sortable scope="col">
                Tjeneste
              </Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Type</Table.ColumnHeader>
              <Table.ColumnHeader sortKey="team" sortable scope="col">
                Team
              </Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Status nå</Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Åpent i dag</Table.ColumnHeader>
              {loggedIn && <Table.ColumnHeader scope="col">Snarveier</Table.ColumnHeader>}
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {filtered.map((service) => {
              const status = daily.data?.[service.id];
              return (
                <Table.Row key={service.id}>
                  <Table.HeaderCell scope="row">
                    <AppLink to={`/t/${service.id}`}>{service.name}</AppLink>
                  </Table.HeaderCell>
                  <Table.DataCell>
                    <Tag variant="neutral" size="small">
                      {service.type === 'TJENESTE' ? 'Tjeneste' : 'Komponent'}
                    </Tag>
                  </Table.DataCell>
                  <Table.DataCell>{service.team}</Table.DataCell>
                  <Table.DataCell>
                    {daily.isPending ? (
                      <Skeleton width="6rem" />
                    ) : (
                      <NowStatus status={status} />
                    )}
                  </Table.DataCell>
                  <Table.DataCell>
                    {status && hasNoRule(status) ? '—' : describeHours(status?.openingHours)}
                  </Table.DataCell>
                  {loggedIn && (
                    <Table.DataCell>
                      <div className="oh-overview__links">
                        {service.monitorlink && (
                          <Link href={service.monitorlink} target="_blank" rel="noreferrer">
                            Overvåkning
                            <ExternalLinkIcon aria-label="Åpnes i ny fane" />
                          </Link>
                        )}
                        {service.logglink && (
                          <Link href={service.logglink} target="_blank" rel="noreferrer">
                            Logger
                            <ExternalLinkIcon aria-label="Åpnes i ny fane" />
                          </Link>
                        )}
                        {!service.monitorlink && !service.logglink && '—'}
                      </div>
                    </Table.DataCell>
                  )}
                </Table.Row>
              );
            })}
          </Table.Body>
        </Table>
      )}
    </div>
  );
}

function NowStatus({
  status,
}: {
  status?: {
    isOpen: boolean;
    redDay: boolean;
    openingHours: string | null;
    ruleName?: string | null;
    unstableOpeningHours?: boolean;
  };
}) {
  // Manglende oppsett kommer tilbake som *døgnåpent* fra dagcachen, ikke som en
  // feil. Uten sjekken ville tjenester uten regler stått som «åpent nå».
  if (!status || hasNoRule({ openingHours: status.openingHours, ruleName: status.ruleName ?? null }))
    return <StatusBadge kind="warning" label="Ikke satt opp" size="small" />;

  const badge = status.redDay ? (
    <StatusBadge kind="redDay" label="Rød dag" size="small" />
  ) : status.isOpen ? (
    <StatusBadge kind="open" label="Åpent nå" size="small" />
  ) : (
    <StatusBadge kind="closed" label="Stengt nå" size="small" />
  );

  if (!status.unstableOpeningHours) return badge;

  // Ustabilitet erstatter ikke statusen, den kommer i tillegg til den.
  return (
    <span className="oh-overview__status">
      {badge}
      <UnstableMark />
      {/* Merket er aria-hidden. Her leses statusen opp direkte, så uten dette
          ville skjermlesere gått glipp av ustabiliteten helt. */}
      <span className="oh-sr-only">Merket som ustabil periode</span>
    </span>
  );
}

function describeHours(hours: string | null | undefined): string {
  if (!hours) return 'Ukjent';
  if (hours === HOURS_ALWAYS_OPEN) return 'Døgnåpen';
  if (hours === HOURS_CLOSED) return 'Stengt i dag';
  return formatHours(hours);
}
