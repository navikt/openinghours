import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BodyLong,
  BodyShort,
  Heading,
  Search,
  Select,
  Skeleton,
  Table,
  Tag,
} from '@navikt/ds-react';
import { useSearchParams } from 'react-router-dom';
import type { ServiceType } from '../api/types';
import { useDailyStatus, useServices, useSession } from '../hooks/queries';
import { formatHours, HOURS_ALWAYS_OPEN, HOURS_CLOSED } from '../lib/rule';
import { StatusBadge } from '../components/calendar/StatusBadge';
import { EmptyState, ErrorState } from '../components/common/ErrorState';
import { DelayedLoader } from '../components/common/DelayedLoader';
import { useNow } from '../hooks/useNow';
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
      <div>
        <Heading level="1" size="xlarge" spacing>
          Åpningstider for Navs tjenester
        </Heading>
        <BodyLong size="large">
          Se når en tjeneste er åpen, i dag og resten av måneden.
        </BodyLong>
      </div>

      <div className="oh-filters">
        <Search
          label="Søk etter tjeneste"
          placeholder="F.eks. dagpenger"
          size="small"
          value={search}
          onChange={(value) => setParam('sok', value)}
          onClear={() => setParam('sok', '')}
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
                    <Link to={`/t/${service.id}`}>{service.name}</Link>
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
                  <Table.DataCell>{describeHours(status?.openingHours)}</Table.DataCell>
                  {loggedIn && (
                    <Table.DataCell>
                      <div className="oh-overview__links">
                        {service.monitorlink && (
                          <a href={service.monitorlink} target="_blank" rel="noreferrer">
                            Overvåkning
                          </a>
                        )}
                        {service.logglink && (
                          <a href={service.logglink} target="_blank" rel="noreferrer">
                            Logger
                          </a>
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

function NowStatus({ status }: { status?: { isOpen: boolean; redDay: boolean; openingHours: string | null } }) {
  if (!status) return <StatusBadge kind="warning" label="Ikke satt opp" size="small" />;
  if (status.redDay) return <StatusBadge kind="redDay" label="Rød dag" size="small" />;
  return status.isOpen ? (
    <StatusBadge kind="open" label="Åpent nå" size="small" />
  ) : (
    <StatusBadge kind="closed" label="Stengt nå" size="small" />
  );
}

function describeHours(hours: string | null | undefined): string {
  if (!hours) return 'Ukjent';
  if (hours === HOURS_ALWAYS_OPEN) return 'Døgnåpen';
  if (hours === HOURS_CLOSED) return 'Stengt i dag';
  return formatHours(hours);
}
