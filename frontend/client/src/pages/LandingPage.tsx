import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert,
  BodyLong,
  BodyShort,
  Box,
  Button,
  Heading,
  Label,
  Search,
  Skeleton,
  Tag,
} from '@navikt/ds-react';
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  ExclamationmarkTriangleFillIcon,
  ExclamationmarkTriangleIcon,
  MinusCircleIcon,
} from '@navikt/aksel-icons';
import type { Bucket, ServiceDay } from '../lib/summary';
import {
  attentionItems,
  dailyToQuery,
  headline,
  summarize,
} from '../lib/summary';
import { useAllServicesRange, useDailyStatus, useServices } from '../hooks/queries';
import { useNow } from '../hooks/useNow';
import { addDays, todayIso, weekdayName } from '../lib/date';
import { AppLink } from '../components/common/AppLink';
import { SummaryBar, SummaryCounts, SummaryLegend } from '../components/overview/SummaryBar';
import { ErrorState } from '../components/common/ErrorState';
import './LandingPage.css';

/** Én uke minus i dag: nok til å planlegge rundt, få nok til å leses på et blikk. */
const STRIP_DAYS = 6;

/**
 * Landingsside — driftsbildet, ikke tjenestelisten.
 *
 * Siden svarer på to spørsmål i rekkefølge: «står det bra til nå?» og «kommer
 * det noe de nærmeste dagene?». Selve listen over 47 tjenester er flyttet til
 * `/tjenester`, fordi den besvarer et tredje spørsmål — «hvor finner jeg X?» —
 * som ingen stiller før de har sett at noe er galt.
 */
export function LandingPage() {
  const navigate = useNavigate();
  const { now, minutes } = useNow();
  const today = todayIso(now);

  const services = useServices();
  const daily = useDailyStatus();
  const [offset, setOffset] = useState(0);
  const [query, setQuery] = useState('');

  const stripStart = addDays(today, 1 + offset * STRIP_DAYS);
  const stripEnd = addDays(stripStart, STRIP_DAYS - 1);

  const serviceIds = useMemo(() => (services.data ?? []).map((s) => s.id), [services.data]);
  const range = useAllServicesRange(serviceIds, stripStart, stripEnd);

  /* I dag kommer fra `/daily`: ett kall for alle tjenester, oppdatert hvert minutt. */
  const todayItems: ServiceDay[] = useMemo(
    () =>
      (services.data ?? []).map((service) => {
        const cached = daily.data?.[service.id];
        return {
          serviceId: service.id,
          serviceName: service.name,
          team: service.team,
          day: cached ? dailyToQuery(cached, today) : null,
        };
      }),
    [services.data, daily.data, today],
  );

  /* Klokkeslettet er med: forsiden lover «åpne nå», ikke «åpne en gang i dag». */
  const todaySummary = useMemo(
    () => summarize(today, todayItems, minutes),
    [today, todayItems, minutes],
  );
  const attention = useMemo(
    () => attentionItems(todaySummary.entries, 5, minutes),
    [todaySummary, minutes],
  );

  /*
   * Stripen bygges av N svar med hver sin periode. Vi indekserer på dato først,
   * slik at et enkelt svar som mangler eller feiler bare gir ett hull i én dag
   * framfor å velte hele stripen.
   */
  const strip = useMemo(() => {
    const list = services.data ?? [];
    return Array.from({ length: STRIP_DAYS }, (_, i) => addDays(stripStart, i)).map((date) =>
      summarize(
        date,
        list.map((service, index) => ({
          serviceId: service.id,
          serviceName: service.name,
          team: service.team,
          day: range[index]?.data?.find((d) => d.date === date) ?? null,
        })),
      ),
    );
  }, [services.data, range.map((r) => r.dataUpdatedAt).join(','), stripStart]);

  const stripPending = range.some((r) => r.isPending) || services.isPending;
  /* Begge kildene må være på plass før tallene betyr noe — se kortet under. */
  const todayPending = services.isPending || daily.isPending || daily.data === undefined;
  const total = services.data?.length ?? 0;

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
    return <ErrorState message="Vi klarte ikke å hente tjenestelisten." onRetry={() => services.refetch()} />;
  }

  return (
    <div className="oh-landing">
      <div>
        <Heading level="1" size="xlarge" spacing>
          Åpningstider for Navs tjenester
        </Heading>
        <BodyLong size="large">Slik ser driften ut i dag og de nærmeste dagene.</BodyLong>
      </div>

      <Box.New
        as="section"
        className="oh-today"
        padding="6"
        borderRadius="large"
        borderWidth="2"
        borderColor="accent"
        background="default"
        aria-label="Status i dag"
      >
        <div className="oh-today__main">
          <div className="oh-today__stamp">
            <Tag variant="info-filled" size="small">
              I dag
            </Tag>
            <BodyShort size="small" textColor="subtle">
              {timestamp}
            </BodyShort>
          </div>

          {daily.isError ? (
            <Alert variant="warning" size="small" inline={false}>
              Vi klarte ikke å hente statusen for i dag. Tallene under ville vært misvisende, så de
              er utelatt. Prøv igjen om litt.
            </Alert>
          ) : todayPending ? (
            /*
             * Alt eller ingenting. Tjenestelisten og statusen kommer fra hvert
             * sitt kall, og i vinduet mellom dem er hver tjeneste uten status —
             * altså «uten åpningstider». Å tegne streken da ville slått ut i full
             * alarm for noe som bare er en halvferdig lasting.
             */
            <>
              <Skeleton variant="text" width="24rem" height="2.5rem" />
              <Skeleton variant="rectangle" height="0.625rem" />
              <Skeleton variant="text" width="18rem" />
            </>
          ) : (
            <>
              <Heading level="2" size="large">
                {headline(todaySummary.counts, todaySummary.total, true)}
              </Heading>
              <SummaryBar
                counts={todaySummary.counts}
                total={todaySummary.total}
                label={`Status i dag: ${headline(todaySummary.counts, todaySummary.total, true)}`}
              />
              <SummaryCounts counts={todaySummary.counts} />
            </>
          )}
        </div>

        <div className="oh-today__aside">
          <Label size="small" textColor="subtle" as="h3">
            Krever oppmerksomhet i dag
          </Label>
          {daily.isError ? (
            <BodyShort size="small" textColor="subtle">
              Ukjent så lenge statusen mangler.
            </BodyShort>
          ) : todayPending ? (
            <Skeleton variant="text" width="14rem" />
          ) : attention.length === 0 ? (
            <BodyShort size="small" textColor="subtle">
              Ingenting å følge opp — alle tjenester er åpne som normalt.
            </BodyShort>
          ) : (
            <ul className="oh-attention">
              {attention.map((entry) => (
                <li key={entry.serviceId} className={`oh-attention__item oh-attention__item--${entry.bucket}`}>
                  <BucketIcon bucket={entry.bucket} />
                  <span>
                    <AppLink to={`/t/${entry.serviceId}`}>{entry.serviceName}</AppLink> {entry.detail}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <AppLink to={`/dag/${today}`}>
            {total ? `Se alle ${total} tjenester i dag` : 'Se alle tjenester i dag'}
          </AppLink>
        </div>
      </Box.New>

      <section className="oh-strip" aria-labelledby="oh-strip-heading">
        <div className="oh-strip__head">
          <Heading level="2" size="small" id="oh-strip-heading">
            {stripHeading(offset)}
          </Heading>
          <div className="oh-strip__nav">
            <Button
              variant="secondary"
              size="small"
              icon={<ArrowLeftIcon aria-hidden />}
              iconPosition="left"
              onClick={() => setOffset((o) => o - 1)}
            >
              Forrige seks dager
            </Button>
            <Button
              variant="secondary"
              size="small"
              icon={<ArrowRightIcon aria-hidden />}
              iconPosition="right"
              onClick={() => setOffset((o) => o + 1)}
            >
              Neste seks dager
            </Button>
          </div>
        </div>

        <ul className="oh-strip__days">
          {strip.map((summary) => (
            <li key={summary.date}>
              <Link to={`/dag/${summary.date}`} className="oh-daycard">
                <span className="oh-daycard__weekday">{weekdayName(summary.date)}</span>
                <span className="oh-daycard__date">{shortDate(summary.date)}</span>
                {stripPending ? (
                  <Skeleton variant="rectangle" height="1.5rem" />
                ) : (
                  <>
                    <SummaryBar
                      counts={summary.counts}
                      total={summary.total}
                      size="small"
                      label={`${weekdayName(summary.date)} ${shortDate(summary.date)}: ${headline(summary.counts, summary.total, false)}`}
                    />
                    {summary.redDay && (
                      <span className="oh-daycard__red">
                        <Tag variant="error" size="small">
                          Rød dag
                        </Tag>
                        {summary.holiday && (
                          <span className="oh-daycard__holiday">{summary.holiday}</span>
                        )}
                      </span>
                    )}
                    <SummaryCounts counts={summary.counts} size="small" />
                  </>
                )}
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <SummaryLegend />

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
          <AppLink to="/tjenester">
            {total ? `Se alle ${total} tjenester` : 'Se alle tjenester'}
          </AppLink>
        </form>
      </section>
    </div>
  );
}

function BucketIcon({ bucket }: { bucket: Bucket }) {
  if (bucket === 'missing') return <ExclamationmarkTriangleIcon aria-hidden fontSize="1.125rem" />;
  if (bucket === 'unstable') return <ExclamationmarkTriangleFillIcon aria-hidden fontSize="1.125rem" />;
  return <MinusCircleIcon aria-hidden fontSize="1.125rem" />;
}

/** «13. mai» — året er underforstått i en stripe som aldri spenner over årsskiftet. */
function shortDate(dateIso: string): string {
  return new Intl.DateTimeFormat('nb-NO', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(
    new Date(`${dateIso}T00:00:00Z`),
  );
}

/**
 * Overskriften følger stripen.
 *
 * Bla man bakover står det fortsatt seks dager i stripen, men «de neste» er da
 * feil — og en overskrift som lyver om hva som står under den er verre enn ingen.
 */
function stripHeading(offset: number): string {
  if (offset === 0) return 'De neste seks dagene';
  if (offset > 0) return `Seks dager fram, ${offset} steg videre`;
  return `Seks dager tilbake, ${Math.abs(offset)} steg bakover`;
}
