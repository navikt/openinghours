import { useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  BodyShort,
  Button,
  Chips,
  Detail,
  Heading,
  Skeleton,
  Table,
  Tag,
} from '@navikt/ds-react';
import { ArrowLeftIcon, ArrowRightIcon } from '@navikt/aksel-icons';
import type { Bucket, DayEntry, ServiceDay } from '../lib/summary';
import { BUCKETS, countLabel, dailyToQuery, presentBuckets, summarize } from '../lib/summary';
import { useAllServicesRange, useDailyStatus, useServices, useSession } from '../hooks/queries';
import { useNow } from '../hooks/useNow';
import { addDays, formatDateLong, todayIso, weekdayName } from '../lib/date';
import { formatRule } from '../lib/rule';
import type { StatusKind } from '../lib/status';
import { statusAriaLabel } from '../lib/status';
import { AppLink } from '../components/common/AppLink';
import { OpeningBar } from '../components/calendar/OpeningBar';
import { StatusBadge, UnstableMark } from '../components/calendar/StatusBadge';
import { SummaryBar, SummaryLegend } from '../components/overview/SummaryBar';
import { ErrorState } from '../components/common/ErrorState';
import './DayPage.css';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/*
 * Merket viser bøtten, ikke den fulle statusteksten: kolonnen ved siden av bærer
 * åpningstiden, og to plasser som sier «08:00–15:30» er én for mye. `unstable`
 * har ingen egen `StatusKind` — den rendres med `UnstableMark` — og står derfor
 * ikke her.
 */
const BUCKET_STATUS: Record<Exclude<Bucket, 'unstable'>, { kind: StatusKind; label: string }> = {
  missing: { kind: 'warning', label: 'Ikke satt opp' },
  closed: { kind: 'closed', label: 'Stengt' },
  open: { kind: 'open', label: 'Åpen' },
};

/**
 * Dagsvisning — én dato, alle tjenester.
 *
 * Rekkefølgen er ikke alfabetisk: uten oppsett først, deretter ustabile, stengte
 * og åpne. Det er avvikene som gjør at noen åpner denne siden, og i en liste på
 * 47 ville de to som mangler oppsett vært umulige å få øye på alfabetisk.
 */
export function DayPage() {
  const { dato } = useParams();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { now, minutes } = useNow();
  const today = todayIso(now);

  const date = dato && ISO_DATE.test(dato) ? dato : today;
  const isToday = date === today;
  const filter = params.get('vis');
  const activeFilter = BUCKETS.includes(filter as Bucket) ? (filter as Bucket) : null;

  const session = useSession();
  const services = useServices();
  const loggedIn = session.data?.loggedIn ?? false;

  /*
   * I dag har sitt eget endepunkt: `/daily` er ett kall for alle tjenester, mot
   * ett kall per tjeneste for enhver annen dato. Vi bruker den billige kilden
   * når vi kan, og fan-out bare når vi må.
   */
  const daily = useDailyStatus();
  const serviceIds = useMemo(() => (services.data ?? []).map((s) => s.id), [services.data]);
  const range = useAllServicesRange(isToday ? [] : serviceIds, date, date);

  const items: ServiceDay[] = useMemo(
    () =>
      (services.data ?? []).map((service, index) => {
        const cached = daily.data?.[service.id];
        const fetched = range[index]?.data?.find((d) => d.date === date) ?? null;
        return {
          serviceId: service.id,
          serviceName: service.name,
          team: service.team,
          day: isToday ? (cached ? dailyToQuery(cached, date) : null) : fetched,
        };
      }),
    [services.data, daily.data, range.map((r) => r.dataUpdatedAt).join(','), date, isToday],
  );

  /*
   * Klokkeslettet gjelder bare i dag. For enhver annen dato finnes det ikke noe
   * «nå», og en tjeneste med åpningstid 08:00–15:30 er da åpen den dagen — ikke
   * stengt fordi klokken tilfeldigvis er 20:00 mens noen ser på den.
   */
  const summary = useMemo(
    () => summarize(date, items, isToday ? minutes : undefined),
    [date, items, isToday, minutes],
  );
  const visible = activeFilter
    ? summary.entries.filter((entry) => entry.bucket === activeFilter)
    : summary.entries;

  const pending = services.isPending || (isToday ? daily.isPending : range.some((r) => r.isPending));
  /*
   * Feiler kilden, blir hver tjeneste stående som «uten åpningstider». Det ser ut
   * som en opplysning, men er fravær av en. Da sier vi det heller rett ut.
   */
  const failed = isToday ? daily.isError : range.filter((r) => r.isError).length;

  const setFilter = (bucket: Bucket | null) => {
    const next = new URLSearchParams(params);
    if (bucket) next.set('vis', bucket);
    else next.delete('vis');
    setParams(next, { replace: true });
  };

  const goto = (delta: number) => navigate(`/dag/${addDays(date, delta)}${params.toString() ? `?${params}` : ''}`);

  if (services.isError) {
    return <ErrorState message="Vi klarte ikke å hente tjenestelisten." onRetry={() => services.refetch()} />;
  }

  return (
    <div className="oh-day">
      <AppLink to="/">Tilbake til oversikten</AppLink>

      <div className="oh-day__head">
        <div>
          <Heading level="1" size="large" spacing>
            {formatDateLong(date)} · {weekdayName(date)}
            {isToday && (
              <>
                {' '}
                <Tag variant="info-filled" size="small">
                  I dag
                </Tag>
              </>
            )}
          </Heading>
          {pending ? (
            <Skeleton variant="text" width="20rem" />
          ) : (
            <BodyShort size="small" textColor="subtle">
              {summary.total} tjenester ·{' '}
              {presentBuckets(summary.counts)
                .map((bucket) => countLabel(bucket, summary.counts[bucket]))
                .join(', ')}
            </BodyShort>
          )}
          {summary.redDay && (
            <div className="oh-day__red">
              <Tag variant="error" size="small">
                Rød dag
              </Tag>
              {summary.holiday && (
                <BodyShort size="small" textColor="subtle">
                  {summary.holiday}
                </BodyShort>
              )}
            </div>
          )}
        </div>

        <div className="oh-day__nav">
          <Button
            variant="secondary"
            size="small"
            icon={<ArrowLeftIcon aria-hidden />}
            iconPosition="left"
            onClick={() => goto(-1)}
          >
            Forrige dag
          </Button>
          <Button
            variant="secondary"
            size="small"
            icon={<ArrowRightIcon aria-hidden />}
            iconPosition="right"
            onClick={() => goto(1)}
          >
            Neste dag
          </Button>
        </div>
      </div>

      {pending ? (
        <Skeleton variant="rectangle" height="0.625rem" />
      ) : (
        <SummaryBar counts={summary.counts} total={summary.total} />
      )}

      {Boolean(failed) && (
        <Alert variant="warning" size="small">
          {isToday
            ? 'Vi klarte ikke å hente statusen for i dag. Tjenestene under står som «ikke satt opp» fordi vi mangler svar, ikke fordi de mangler åpningstider.'
            : `Vi klarte ikke å hente åpningstidene for ${failed} av ${summary.total} tjenester. De står som «ikke satt opp» fordi vi mangler svar.`}
        </Alert>
      )}

      {/*
        Filtrene er bare bøttene som faktisk har treff denne dagen. En knapp som
        alltid gir null treff er en blindvei, og en dag uten avvik skal se ut som
        en dag uten avvik.
      */}
      <Chips>
        {(pending ? [] : presentBuckets(summary.counts)).map((bucket) => (
          <Chips.Toggle
            key={bucket}
            selected={activeFilter === bucket}
            onClick={() => setFilter(activeFilter === bucket ? null : bucket)}
          >
            {countLabel(bucket, summary.counts[bucket])}
          </Chips.Toggle>
        ))}
        {activeFilter && (
          <Chips.Removable onClick={() => setFilter(null)}>Vis alle</Chips.Removable>
        )}
      </Chips>

      {pending && <Skeleton variant="rectangle" height="20rem" />}

      {!pending && (
        <Table size="small" zebraStripes={false}>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeader scope="col">Tjeneste</Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Status denne dagen</Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Åpningstid</Table.ColumnHeader>
              <Table.ColumnHeader scope="col">Melding til brukeren</Table.ColumnHeader>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {visible.map((entry) => (
              <DayRow key={entry.serviceId} entry={entry} loggedIn={loggedIn} />
            ))}
          </Table.Body>
        </Table>
      )}

      {!pending && visible.length === 0 && (
        <BodyShort>Ingen tjenester i denne kategorien denne dagen.</BodyShort>
      )}

      <SummaryLegend />

      <Detail textColor="subtle">
        Rekkefølgen er ikke alfabetisk: uten oppsett først, deretter ustabile, stengte og åpne. Det
        er avvikene som gjør at noen åpner denne siden.
        {loggedIn && ' Regelnavnet under meldingen vises bare for innloggede.'}
      </Detail>
    </div>
  );
}

function DayRow({ entry, loggedIn }: { entry: DayEntry; loggedIn: boolean }) {
  const { status, day } = entry;
  const badge = entry.bucket === 'unstable' ? null : BUCKET_STATUS[entry.bucket];

  return (
    <Table.Row>
      <Table.HeaderCell scope="row">
        <AppLink to={`/t/${entry.serviceId}`}>{entry.serviceName}</AppLink>
        <Detail textColor="subtle">{entry.team}</Detail>
      </Table.HeaderCell>

      <Table.DataCell>
        {/*
          Merket er dekorativt, og hele statusen ligger i den skjulte teksten.
          `aria-label` på en naken `span` gir ingen tilgjengelig navn — elementet
          har ingen rolle å henge navnet på — så cellen ville vært tom for
          skjermlesere. Teksten må stå som ekte innhold.
        */}
        <span className="oh-day__status">
          {badge ? (
            <StatusBadge kind={badge.kind} label={badge.label} size="small" decorative />
          ) : (
            <UnstableMark />
          )}
          <span className="oh-sr-only">
            {day && status ? statusAriaLabel(day, status) : `${entry.serviceName}: ikke satt opp`}
          </span>
        </span>
      </Table.DataCell>

      <Table.DataCell>
        {status ? (
          <div className="oh-day__hours">
            <BodyShort size="small" weight="semibold">
              {status.allDay ? 'Døgnåpen' : status.label.replace('Åpen ', '')}
            </BodyShort>
            {(status.intervals.length > 0 || status.allDay) && (
              <OpeningBar intervals={status.intervals} allDay={status.allDay} />
            )}
          </div>
        ) : (
          <BodyShort size="small" weight="semibold">
            Ukjent
          </BodyShort>
        )}
      </Table.DataCell>

      <Table.DataCell>
        <div className="oh-day__message">
          {day?.warningMessage || !day ? (
            <BodyShort size="small">Vi kan ikke si når tjenesten er åpen denne dagen.</BodyShort>
          ) : (
            <>
              {day.displayHeader && (
                <BodyShort size="small" weight="semibold">
                  {day.displayHeader}
                </BodyShort>
              )}
              {day.displayText && <BodyShort size="small">{day.displayText}</BodyShort>}
              {status?.unstable && (
                <BodyShort size="small">
                  Perioden er markert som ustabil. Åpningstiden gjelder, men kan svikte.
                </BodyShort>
              )}
            </>
          )}
          {loggedIn && (
            <Detail textColor="subtle">
              {day?.matchedRule ? `Regel: ${day.matchedRule.name}` : 'Regel: ingen regel traff'}
              {day?.matchedRule && ` · ${formatRule(day.matchedRule.rule)}`}
            </Detail>
          )}
        </div>
      </Table.DataCell>
    </Table.Row>
  );
}
