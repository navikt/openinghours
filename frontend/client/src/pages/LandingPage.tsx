import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  BodyLong,
  BodyShort,
  Box,
  Button,
  Heading,
  Search,
  Skeleton,
  Tag,
} from '@navikt/ds-react';
import { ChevronLeftIcon, ChevronRightIcon } from '@navikt/aksel-icons';
import type { DeviationEntry } from '../lib/deviation';
import { DEVIATION_KINDS, DEVIATION_LABELS, buildCalendar, upcoming } from '../lib/deviation';
import { useAllServicesRange, useServices } from '../hooks/queries';
import { useNow } from '../hooks/useNow';
import { formatMonth, monthGrid, monthOf, shiftMonth, todayIso } from '../lib/date';
import { AppLink } from '../components/common/AppLink';
import { DeviationLegend, DeviationMonth } from '../components/overview/DeviationMonth';
import { ErrorState } from '../components/common/ErrorState';
import '../components/overview/DeviationMark.css';
import './LandingPage.css';

const ISO_MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * Landingsside — avvikskalenderen.
 *
 * Premisset: den som åpner siden kjenner allerede tjenestenes normale
 * åpningstider. Det de kommer for å finne ut er hva som *bryter* med dem, og
 * når. Siden viser derfor bare avvik, i en kalender som kan skummes: er en dag
 * tom, er det ingenting å vite om den dagen.
 *
 * Hva som er «normalt» avgjøres ikke her, men av tjenestens egne regler — se
 * `lib/deviation.ts`.
 */
export function LandingPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { now } = useNow();
  const today = todayIso(now);
  const [query, setQuery] = useState('');

  const raw = params.get('maned');
  const month = raw && ISO_MONTH.test(raw) ? raw : monthOf(today);

  const services = useServices();
  const serviceIds = useMemo(() => (services.data ?? []).map((s) => s.id), [services.data]);

  /*
   * Hele månedsrutenettet i ett kall per tjeneste — også dagene fra nabo-
   * månedene. Normalplanen utledes fra de samme dagene, og seks uker gir fire
   * til seks observasjoner per ukedag. Med et kortere vindu kunne en enkelt
   * kortdag flyttet «normalen» for hele ukedagen.
   */
  const grid = useMemo(() => monthGrid(month), [month]);
  const from = grid[0].date;
  const to = grid[grid.length - 1].date;
  const range = useAllServicesRange(serviceIds, from, to);

  const pending = services.isPending || range.some((r) => r.isPending);
  const failed = range.filter((r) => r.isError).length;

  const calendar = useMemo(() => {
    const list = services.data ?? [];
    return buildCalendar(
      list.map((service, index) => ({
        serviceId: service.id,
        serviceName: service.name,
        team: service.team,
        days: range[index]?.data ?? [],
      })),
      month,
    );
  }, [services.data, range.map((r) => r.dataUpdatedAt).join(','), month]);

  const todayEntries = calendar.byDate.get(today) ?? [];
  const showsToday = grid.some((d) => d.inMonth && d.date === today);
  const next = useMemo(() => upcoming(calendar, today, 8), [calendar, today]);

  /* Tegnforklaringen nevner bare det som faktisk står i kalenderen. */
  const kinds = useMemo(() => {
    const present = new Set<string>();
    for (const entries of calendar.byDate.values()) {
      for (const entry of entries) present.add(entry.deviation.kind);
    }
    return DEVIATION_KINDS.filter((k) => present.has(k));
  }, [calendar]);

  const setMonth = (target: string) => {
    const updated = new URLSearchParams(params);
    // Inneværende måned er standardvisningen og trenger ingen parameter.
    if (target === monthOf(today)) updated.delete('maned');
    else updated.set('maned', target);
    setParams(updated, { replace: true });
  };

  if (services.isError) {
    return (
      <ErrorState
        message="Vi klarte ikke å hente tjenestelisten."
        onRetry={() => services.refetch()}
      />
    );
  }

  return (
    <div className="oh-landing">
      <div>
        <Heading level="1" size="xlarge" spacing>
          Avvik i åpningstidene
        </Heading>
        <BodyLong size="large">
          Kalenderen viser bare dagene som bryter med tjenestenes normale åpningstider. Er en dag
          tom, er alt som det pleier.
        </BodyLong>
      </div>

      <Box.New
        as="section"
        className="oh-today"
        padding="6"
        borderRadius="large"
        borderWidth="2"
        borderColor="accent"
        background="default"
        aria-label="Avvik i dag og framover"
      >
        <div className="oh-today__main">
          <Tag variant="info-filled" size="small">
            I dag
          </Tag>
          {pending ? (
            <Skeleton variant="text" width="20rem" height="2.25rem" />
          ) : (
            <Heading level="2" size="large">
              {showsToday
                ? todayHeadline(todayEntries.length)
                : `Du ser på ${formatMonth(month).toLowerCase()}`}
            </Heading>
          )}
          {showsToday && !pending && todayEntries.length > 0 && (
            <ul className="oh-devlist">
              {todayEntries.slice(0, 4).map((entry) => (
                <DeviationLine key={entry.serviceId} entry={entry} />
              ))}
            </ul>
          )}
          <AppLink to={`/dag/${today}`}>Se alle tjenester i dag</AppLink>
        </div>

        <div className="oh-today__aside">
          <Heading level="3" size="xsmall">
            Neste avvik
          </Heading>
          {pending ? (
            <Skeleton variant="text" width="14rem" />
          ) : next.length === 0 ? (
            <BodyShort size="small" textColor="subtle">
              Ingen avvik igjen i {formatMonth(month).toLowerCase()}.
            </BodyShort>
          ) : (
            <ul className="oh-devlist">
              {next.map((entry) => (
                <DeviationLine key={`${entry.date}-${entry.serviceId}`} entry={entry} withDate />
              ))}
            </ul>
          )}
        </div>
      </Box.New>

      {failed > 0 && (
        <Alert variant="warning" size="small">
          {failed === 1
            ? 'Én tjeneste svarte ikke og er utelatt fra kalenderen.'
            : `${failed} tjenester svarte ikke og er utelatt fra kalenderen.`}{' '}
          Avvik kan mangle uten at kalenderen sier fra.
        </Alert>
      )}

      {calendar.unconfigured.length > 0 && (
        <Alert variant="warning" size="small">
          {/* Én linje framfor 42 røde dager: en tjeneste uten regler har ingen
              normal å avvike fra, og ville ellers farget hele kalenderen. */}
          <BodyShort size="small" spacing>
            {calendar.unconfigured.length === 1
              ? 'Én tjeneste har ingen regler som treffer, og har derfor ingen normal å måle avvik mot:'
              : `${calendar.unconfigured.length} tjenester har ingen regler som treffer, og har derfor ingen normal å måle avvik mot:`}
          </BodyShort>
          <ul className="oh-unconfigured">
            {calendar.unconfigured.map((service) => (
              <li key={service.serviceId}>
                <AppLink to={`/t/${service.serviceId}`}>{service.serviceName}</AppLink>
              </li>
            ))}
          </ul>
        </Alert>
      )}

      <section className="oh-month" aria-labelledby="oh-month-heading">
        <div className="oh-month__head">
          <div className="oh-month__title">
            <Heading level="2" size="medium" id="oh-month-heading">
              {formatMonth(month)}
            </Heading>
            <BodyShort size="small" textColor="subtle">
              {pending ? 'Henter …' : monthCount(calendar.total)}
            </BodyShort>
          </div>
          <div className="oh-month__nav">
            <Button
              variant="secondary"
              size="small"
              icon={<ChevronLeftIcon aria-hidden />}
              onClick={() => setMonth(shiftMonth(month, -1))}
            >
              Forrige
            </Button>
            {month !== monthOf(today) && (
              <Button variant="secondary" size="small" onClick={() => setMonth(monthOf(today))}>
                I dag
              </Button>
            )}
            <Button
              variant="secondary"
              size="small"
              icon={<ChevronRightIcon aria-hidden />}
              iconPosition="right"
              onClick={() => setMonth(shiftMonth(month, 1))}
            >
              Neste
            </Button>
          </div>
        </div>

        <DeviationMonth
          month={month}
          calendar={calendar}
          today={today}
          pending={pending}
          onNavigateMonth={(target) => setMonth(target)}
          onSelect={(date) => navigate(`/dag/${date}`)}
        />

        <DeviationLegend kinds={kinds} />
      </section>

      <section className="oh-find" aria-labelledby="oh-find-heading">
        <Heading level="2" size="small" id="oh-find-heading" spacing>
          Finn en tjeneste
        </Heading>
        <form
          className="oh-find__row"
          onSubmit={(event) => {
            event.preventDefault();
            navigate(query ? `/tjenester?sok=${encodeURIComponent(query)}` : '/tjenester');
          }}
        >
          <Search
            label="Søk etter en tjeneste"
            placeholder="F.eks. dagpenger"
            variant="primary"
            value={query}
            onChange={setQuery}
            onClear={() => setQuery('')}
            className="oh-find__search"
          />
          <AppLink to="/tjenester">Se alle tjenester</AppLink>
        </form>
      </section>
    </div>
  );
}

function DeviationLine({ entry, withDate = false }: { entry: DeviationEntry; withDate?: boolean }) {
  return (
    <li className={`oh-devline oh-devchip--${entry.deviation.kind}`}>
      <span className="oh-devline__top">
        {withDate && (
          <Link to={`/dag/${entry.date}`} className="oh-devline__date">
            {shortDate(entry.date)}
          </Link>
        )}
        <AppLink to={`/t/${entry.serviceId}`}>{entry.serviceName}</AppLink>
      </span>
      <span className="oh-devline__what">
        {entry.deviation.summary}
        {entry.deviation.normally && ` · ${entry.deviation.normally}`}
      </span>
      {/* Kun for skjermlesere: fargestripen til venstre sier det samme visuelt. */}
      <span className="oh-sr-only">{DEVIATION_LABELS[entry.deviation.kind]}</span>
    </li>
  );
}

function todayHeadline(count: number): string {
  if (count === 0) return 'Ingen avvik i dag';
  return count === 1 ? 'Ett avvik i dag' : `${count} avvik i dag`;
}

function monthCount(total: number): string {
  if (total === 0) return 'Ingen avvik denne måneden';
  return total === 1 ? 'Ett avvik denne måneden' : `${total} avvik denne måneden`;
}

/** «13. mai» — året står allerede i månedsoverskriften. */
function shortDate(dateIso: string): string {
  return new Intl.DateTimeFormat('nb-NO', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${dateIso}T00:00:00Z`));
}
