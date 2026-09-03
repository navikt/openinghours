import { useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  BodyShort,
  Button,
  Detail,
  ExpansionCard,
  Heading,
  Skeleton,
  Table,
  Tag,
} from '@navikt/ds-react';
import { ArrowLeftIcon, ArrowRightIcon } from '@navikt/aksel-icons';
import type { DeviationEntry } from '../lib/deviation';
import { DEVIATION_LABELS, buildCalendar, describeSignature, deriveBaseline } from '../lib/deviation';
import { useAllServicesRange, useServices, useSession } from '../hooks/queries';
import { useNow } from '../hooks/useNow';
import { addDays, formatDateLong, isoWeekday, monthGrid, monthOf, todayIso, weekdayName } from '../lib/date';
import { formatRule } from '../lib/rule';
import { deriveStatus, statusAriaLabel } from '../lib/status';
import type { DayStatus } from '../lib/status';
import type { QueryResponse } from '../api/types';
import { AppLink } from '../components/common/AppLink';
import { OpeningBar } from '../components/calendar/OpeningBar';
import { ErrorState } from '../components/common/ErrorState';
import '../components/overview/DeviationMark.css';
import './DayPage.css';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

interface NormalRow {
  serviceId: string;
  serviceName: string;
  team: string;
  day: QueryResponse | null;
  status: DayStatus | null;
}

/**
 * Dagsvisning — én dato, avvikene først.
 *
 * Avvikene står øverst og i sin helhet; tjenestene som følger sin vanlige
 * timeplan er slått sammen bak én utvidbar linje. Det er en bevisst asymmetri:
 * en liste der 45 normale tjenester står side om side med de to som har endret
 * seg, skjuler nettopp det siden er til for å vise.
 *
 * Vinduet som hentes er hele månedsrutenettet, ikke bare denne ene dagen.
 * Normalplanen kan bare utledes ved å se flere uker av samme ukedag — og
 * nøkkelen er den samme som forsidens, så et klikk derfra treffer cachen.
 */
export function DayPage() {
  const { dato } = useParams();
  const navigate = useNavigate();
  const { now } = useNow();
  const today = todayIso(now);

  const date = dato && ISO_DATE.test(dato) ? dato : today;
  const isToday = date === today;

  const session = useSession();
  const services = useServices();
  const loggedIn = session.data?.loggedIn ?? false;

  const serviceIds = useMemo(() => (services.data ?? []).map((s) => s.id), [services.data]);
  const grid = useMemo(() => monthGrid(monthOf(date)), [date]);
  const range = useAllServicesRange(serviceIds, grid[0].date, grid[grid.length - 1].date);

  const pending = services.isPending || range.some((r) => r.isPending);
  const failed = range.filter((r) => r.isError).length;

  const serviceDays = useMemo(
    () =>
      (services.data ?? []).map((service, index) => ({
        serviceId: service.id,
        serviceName: service.name,
        team: service.team,
        days: range[index]?.data ?? [],
      })),
    [services.data, range.map((r) => r.dataUpdatedAt).join(',')],
  );

  const calendar = useMemo(() => buildCalendar(serviceDays, monthOf(date)), [serviceDays, date]);
  const deviations = calendar.byDate.get(date) ?? [];

  /*
   * «Som vanlig» er alt som ikke er et avvik — inkludert tjenestene uten
   * oppsett, som ikke har noen normal å avvike fra. De hører hjemme i lista,
   * ikke i avviksseksjonen, der de ville ropt like høyt hver eneste dag.
   */
  const normal: NormalRow[] = useMemo(() => {
    const deviating = new Set(deviations.map((d) => d.serviceId));
    return serviceDays
      .filter((service) => !deviating.has(service.serviceId))
      .map((service) => {
        const day = service.days.find((d) => d.date === date) ?? null;
        return {
          serviceId: service.serviceId,
          serviceName: service.serviceName,
          team: service.team,
          day,
          status: day ? deriveStatus(day) : null,
        };
      })
      .sort((a, b) => a.serviceName.localeCompare(b.serviceName, 'nb'));
  }, [serviceDays, deviations, date]);

  /** Normalplanen for ukedagen, per tjeneste — brukes i «som vanlig»-lista. */
  const baselines = useMemo(() => {
    const weekday = isoWeekday(date);
    const map = new Map<string, string>();
    for (const service of serviceDays) {
      const sig = deriveBaseline(service.days, monthOf(date)).byWeekday.get(weekday);
      if (sig) map.set(service.serviceId, describeSignature(sig));
    }
    return map;
  }, [serviceDays, date]);

  const holiday = deviations.find((d) => d.deviation.holiday)?.deviation.holiday ?? null;
  const goto = (delta: number) => navigate(`/dag/${addDays(date, delta)}`);

  if (services.isError) {
    return (
      <ErrorState
        message="Vi klarte ikke å hente tjenestelisten."
        onRetry={() => services.refetch()}
      />
    );
  }

  return (
    <div className="oh-day">
      <AppLink to="/">Tilbake til kalenderen</AppLink>

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
            <Skeleton variant="text" width="18rem" />
          ) : (
            <BodyShort size="small" textColor="subtle">
              {headline(deviations.length, serviceDays.length)}
            </BodyShort>
          )}
          {holiday && (
            <div className="oh-day__red">
              <Tag variant="error" size="small">
                Rød dag
              </Tag>
              <BodyShort size="small" textColor="subtle">
                {holiday}
              </BodyShort>
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

      {failed > 0 && (
        <Alert variant="warning" size="small">
          {failed === 1
            ? 'Én tjeneste svarte ikke.'
            : `${failed} tjenester svarte ikke.`}{' '}
          Avvik kan mangle uten at siden sier fra.
        </Alert>
      )}

      {pending ? (
        <Skeleton variant="rectangle" height="12rem" />
      ) : deviations.length === 0 ? (
        <Alert variant="success" size="small" inline={false}>
          Alle tjenester følger sin vanlige timeplan denne dagen.
        </Alert>
      ) : (
        <section aria-labelledby="oh-dev-heading" className="oh-day__section">
          <Heading level="2" size="small" id="oh-dev-heading">
            Avvik denne dagen
          </Heading>
          <Table size="small" zebraStripes={false}>
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeader scope="col">Tjeneste</Table.ColumnHeader>
                <Table.ColumnHeader scope="col">Avvik</Table.ColumnHeader>
                <Table.ColumnHeader scope="col">Denne dagen</Table.ColumnHeader>
                <Table.ColumnHeader scope="col">Melding til brukeren</Table.ColumnHeader>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {deviations.map((entry) => (
                <DeviationRow key={entry.serviceId} entry={entry} loggedIn={loggedIn} />
              ))}
            </Table.Body>
          </Table>
        </section>
      )}

      {!pending && normal.length > 0 && (
        <ExpansionCard size="small" aria-label="Tjenester som følger sin vanlige timeplan">
          <ExpansionCard.Header>
            <ExpansionCard.Title size="small">
              Som vanlig ({normal.length}{' '}
              {normal.length === 1 ? 'tjeneste' : 'tjenester'})
            </ExpansionCard.Title>
            <ExpansionCard.Description>
              Disse følger timeplanen sin denne dagen. Åpne for å slå opp en bestemt tjeneste.
            </ExpansionCard.Description>
          </ExpansionCard.Header>
          <ExpansionCard.Content>
            <Table size="small" zebraStripes={false}>
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeader scope="col">Tjeneste</Table.ColumnHeader>
                  <Table.ColumnHeader scope="col">Åpningstid</Table.ColumnHeader>
                  <Table.ColumnHeader scope="col">Normalt</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {normal.map((row) => (
                  <Table.Row key={row.serviceId}>
                    <Table.HeaderCell scope="row">
                      <AppLink to={`/t/${row.serviceId}`}>{row.serviceName}</AppLink>
                      <Detail textColor="subtle">{row.team}</Detail>
                    </Table.HeaderCell>
                    <Table.DataCell>
                      {row.status && row.day ? (
                        <div className="oh-day__hours">
                          <BodyShort size="small" weight="semibold">
                            {row.status.allDay
                              ? 'Døgnåpen'
                              : row.status.label.replace('Åpen ', '')}
                          </BodyShort>
                          <span className="oh-sr-only">
                            {statusAriaLabel(row.day, row.status)}
                          </span>
                          {(row.status.intervals.length > 0 || row.status.allDay) && (
                            <OpeningBar
                              intervals={row.status.intervals}
                              allDay={row.status.allDay}
                            />
                          )}
                        </div>
                      ) : (
                        <BodyShort size="small">Ukjent</BodyShort>
                      )}
                    </Table.DataCell>
                    <Table.DataCell>
                      <BodyShort size="small" textColor="subtle">
                        {baselines.get(row.serviceId) ?? 'ukjent normalplan'}
                      </BodyShort>
                    </Table.DataCell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table>
          </ExpansionCard.Content>
        </ExpansionCard>
      )}

      <Detail textColor="subtle">
        «Normalt» er utledet av tjenestens egne regler: den timeplanen som gjentar seg hver uke.
        Avvik er dagene som bryter med den.
        {loggedIn && ' Regelnavnet vises bare for innloggede.'}
      </Detail>
    </div>
  );
}

function DeviationRow({ entry, loggedIn }: { entry: DeviationEntry; loggedIn: boolean }) {
  const { deviation, day } = entry;
  const status = deriveStatus(day);

  return (
    <Table.Row>
      <Table.HeaderCell scope="row">
        <AppLink to={`/t/${entry.serviceId}`}>{entry.serviceName}</AppLink>
        <Detail textColor="subtle">{entry.team}</Detail>
      </Table.HeaderCell>

      <Table.DataCell>
        <span className={`oh-devmark oh-devchip--${deviation.kind}`}>
          <BodyShort size="small" weight="semibold">
            {DEVIATION_LABELS[deviation.kind]}
          </BodyShort>
          {deviation.normally && (
            <BodyShort size="small" textColor="subtle">
              {capitalize(deviation.normally)}
            </BodyShort>
          )}
          {deviation.unstable && deviation.kind !== 'unstable' && (
            <BodyShort size="small" textColor="subtle">
              Også markert som ustabil
            </BodyShort>
          )}
        </span>
      </Table.DataCell>

      <Table.DataCell>
        <div className="oh-day__hours">
          <BodyShort size="small" weight="semibold">
            {deviation.summary}
          </BodyShort>
          {/* Full status som tekst: merket over er komprimert, og en skjermleser
              skal ikke måtte sette sammen betydningen av to fragmenter. */}
          <span className="oh-sr-only">{statusAriaLabel(day, status)}</span>
          {(status.intervals.length > 0 || status.allDay) && (
            <OpeningBar intervals={status.intervals} allDay={status.allDay} />
          )}
        </div>
      </Table.DataCell>

      <Table.DataCell>
        <div className="oh-day__message">
          {day.warningMessage ? (
            <BodyShort size="small">Vi kan ikke si når tjenesten er åpen denne dagen.</BodyShort>
          ) : (
            <>
              {day.displayHeader && (
                <BodyShort size="small" weight="semibold">
                  {day.displayHeader}
                </BodyShort>
              )}
              {day.displayText && <BodyShort size="small">{day.displayText}</BodyShort>}
            </>
          )}
          {loggedIn && (
            <Detail textColor="subtle">
              {day.matchedRule ? `Regel: ${day.matchedRule.name}` : 'Regel: ingen regel traff'}
              {day.matchedRule && ` · ${formatRule(day.matchedRule.rule)}`}
            </Detail>
          )}
        </div>
      </Table.DataCell>
    </Table.Row>
  );
}

function headline(deviations: number, total: number): string {
  if (total === 0) return 'Ingen tjenester';
  if (deviations === 0) return `${total} tjenester · alle som vanlig`;
  return `${total} tjenester · ${deviations} avvik`;
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
