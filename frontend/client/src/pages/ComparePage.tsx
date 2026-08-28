import { useEffect, useMemo } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import {
  Alert,
  BodyShort,
  Button,
  Chips,
  DatePicker,
  Detail,
  Heading,
  Link,
  Skeleton,
  useDatepicker,
} from '@navikt/ds-react';
import { ArrowLeftIcon } from '@navikt/aksel-icons';
import { useServices, useServicesOnDate, useSession } from '../hooks/queries';
import { addDays, dateToIso, formatDateLong, isoToDate, todayIso } from '../lib/date';
import { deriveStatus, statusAriaLabel } from '../lib/status';
import { useNow } from '../hooks/useNow';
import { OpeningBar, TimeAxis } from '../components/calendar/OpeningBar';
import { StatusBadge } from '../components/calendar/StatusBadge';
import { DelayedLoader } from '../components/common/DelayedLoader';
import { ErrorState } from '../components/common/ErrorState';
import './ComparePage.css';

/** Flere enn dette blir uleselig, og hvert valg koster ett kall mot backend. */
const MAX_SERVICES = 6;

/**
 * Sammenlign tjenester — én dato, flere tjenester på samme tidsakse.
 *
 * Backend har ingen samlet endepunkt, så siden gjør ett kall per valgt tjeneste.
 * Derfor er antallet begrenset, og hver rad viser sin egen feil i stedet for at
 * én treg tjeneste skjuler hele sammenligningen.
 */
export function ComparePage() {
  const [params, setParams] = useSearchParams();
  const { now } = useNow();
  const today = todayIso(now);

  const date = params.get('dato') ?? today;
  const selectedIds = useMemo(
    () => (params.get('tjenester') ?? '').split(',').filter(Boolean),
    [params],
  );

  const session = useSession();
  const services = useServices();
  const results = useServicesOnDate(selectedIds, date);

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value === null || value === '') next.delete(key);
    else next.set(key, value);
    setParams(next, { replace: true });
  };

  const { datepickerProps, inputProps, setSelected } = useDatepicker({
    defaultSelected: isoToDate(date),
    onDateChange: (picked) => picked && setParam('dato', dateToIso(picked)),
  });

  /*
   * Datoen er URL-en, ikke kalenderens interne tilstand. Snarveiknappene under
   * skriver rett til URL-en, og uten denne synkroniseringen ville feltet blitt
   * stående på gårsdagens verdi mens tabellen viste en annen dag.
   * `setSelected` er bevisst utelatt fra avhengighetene — den er ustabil mellom
   * renders og ville gitt en evig løkke.
   */
  useEffect(() => {
    setSelected(isoToDate(date));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  const toggle = (id: string) => {    const next = selectedIds.includes(id)
      ? selectedIds.filter((s) => s !== id)
      : [...selectedIds, id].slice(0, MAX_SERVICES);
    setParam('tjenester', next.join(','));
  };

  const atLimit = selectedIds.length >= MAX_SERVICES;
  const byId = new Map((services.data ?? []).map((s) => [s.id, s]));

  return (
    <div className="oh-page">
      <Link as={RouterLink} to="/" className="oh-back">
        <ArrowLeftIcon aria-hidden /> Tilbake til alle tjenester
      </Link>

      <Heading level="1" size="xlarge">
        Sammenlign tjenester
      </Heading>
      <BodyShort textColor="subtle">
        Velg en dato og inntil {MAX_SERVICES} tjenester for å se åpningstidene på samme tidsakse.
      </BodyShort>

      <div className="oh-compare__controls">
        <div className="oh-compare__date">
          <DatePicker {...datepickerProps}>
            <DatePicker.Input {...inputProps} id="oh-compare-date" label="Dato" size="small" />
          </DatePicker>
          <div className="oh-compare__shortcuts">
            <Button variant="tertiary" size="small" onClick={() => setParam('dato', today)}>
              I dag
            </Button>
            <Button
              variant="tertiary"
              size="small"
              onClick={() => setParam('dato', addDays(date, -1))}
            >
              Dagen før
            </Button>
            <Button
              variant="tertiary"
              size="small"
              onClick={() => setParam('dato', addDays(date, 1))}
            >
              Dagen etter
            </Button>
          </div>
        </div>

        <fieldset className="oh-compare__picker">
          <legend>Tjenester</legend>
          {services.isPending && <Skeleton width="24rem" />}
          {services.isError && (
            <ErrorState
              message="Vi klarte ikke å hente tjenestelista."
              onRetry={() => services.refetch()}
            />
          )}
          <Chips>
            {(services.data ?? []).map((service) => {
              const active = selectedIds.includes(service.id);
              return (
                <Chips.Toggle
                  key={service.id}
                  selected={active}
                  checkmark
                  // Uten dette kan brukeren klikke seg forbi grensen på seks.
                  disabled={!active && atLimit}
                  onClick={() => toggle(service.id)}
                >
                  {service.name}
                </Chips.Toggle>
              );
            })}
          </Chips>
        </fieldset>
      </div>

      {atLimit && (
        <Alert variant="info" size="small" inline>
          Du kan sammenligne inntil {MAX_SERVICES} tjenester om gangen.
        </Alert>
      )}

      <Heading level="2" size="small">
        {capitalize(formatDateLong(date))}
      </Heading>

      {selectedIds.length === 0 ? (
        <Alert variant="info">Velg minst én tjeneste for å se en sammenligning.</Alert>
      ) : (
        <div className="oh-compare">
          <div className="oh-compare__head" aria-hidden>
            <div className="oh-compare__name" />
            <div className="oh-compare__track">
              <TimeAxis />
            </div>
          </div>

          <ul className="oh-compare__rows">
            {selectedIds.map((id, index) => {
              const result = results[index];
              const service = byId.get(id);
              const day = result?.data;
              const status = day ? deriveStatus(day) : null;

              return (
                <li className="oh-compare__row" key={id}>
                  <div className="oh-compare__name">
                    <BodyShort weight="semibold" as="span">
                      {service?.name ?? day?.serviceName ?? 'Ukjent tjeneste'}
                    </BodyShort>
                    {service && (
                      <Detail as="span" textColor="subtle">
                        team {service.team}
                      </Detail>
                    )}
                  </div>

                  <div className="oh-compare__track">
                    {result?.isPending && <DelayedLoader />}
                    {result?.isError && (
                      <ErrorState
                        message={`Vi klarte ikke å hente åpningstidene for ${service?.name ?? 'tjenesten'}.`}
                        onRetry={() => result.refetch()}
                      />
                    )}
                    {day && status && (
                      <div aria-label={statusAriaLabel(day, status)} role="img">
                        <OpeningBar
                          intervals={status.intervals}
                          allDay={status.allDay}
                          variant="track"
                          showTimes
                        />
                        <div className="oh-compare__status">
                          <StatusBadge
                            kind={status.kind}
                            label={status.label}
                            size="small"
                            decorative
                          />
                          {status.detail && (
                            <Detail as="span" textColor="subtle">
                              {status.detail}
                            </Detail>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {!session.data?.loggedIn && (
        <Detail textColor="subtle">
          Dager med intern åpningstid vises som «Intern åpningstid». Logg inn som ansatt for å se
          dem.
        </Detail>
      )}
    </div>
  );
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
