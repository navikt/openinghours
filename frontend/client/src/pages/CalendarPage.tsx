import { useEffect, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  BodyShort,
  Button,
  Heading,
  Loader,
  Modal,
  MonthPicker,
  Select,
  Skeleton,
  Tabs,
  useMonthpicker,
} from '@navikt/ds-react';
import { ArrowLeftIcon, ChevronLeftIcon, ChevronRightIcon } from '@navikt/aksel-icons';
import { useNavigate } from 'react-router-dom';
import { useService, useServiceRange, useServices, useSession } from '../hooks/queries';
import {
  dateToIso,
  firstOfMonth,
  isoToDate,
  monthOf,
  startOfWeek,
  todayIso,
  yearOf,
} from '../lib/date';
import { parseView, shiftAnchor, viewRange, type ViewKind } from '../lib/view';
import { useNow } from '../hooks/useNow';
import { MonthGrid } from '../components/calendar/MonthGrid';
import { WeekView } from '../components/calendar/WeekView';
import { YearView } from '../components/calendar/YearView';
import { AgendaList } from '../components/calendar/AgendaList';
import { DayDetailPanel } from '../components/calendar/DayDetailPanel';
import { Legend } from '../components/calendar/Legend';
import { DelayedLoader } from '../components/common/DelayedLoader';
import { ErrorState } from '../components/common/ErrorState';
import { AppLink } from '../components/common/AppLink';
import './CalendarPage.css';

export function CalendarPage() {
  const { serviceId } = useParams<{ serviceId: string }>();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { now } = useNow();
  const today = todayIso(now);

  const view = parseView(params.get('visning'));
  // Ett anker for alle tre visningene: da beholder brukeren tidspunktet sitt
  // når hen bytter mellom måned, uke og år.
  const anchor = params.get('dag') ?? params.get('dato') ?? today;
  const selected = params.get('dato');
  const isMobile = useIsMobile();

  const { from, to, title, unit } = viewRange(view, anchor);

  const session = useSession();
  const service = useService(serviceId);
  const services = useServices();
  const range = useServiceRange(serviceId, from, to);

  const headingRef = useRef<HTMLHeadingElement>(null);
  const [announcement, setAnnouncement] = useState('');

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value === null) next.delete(key);
    else next.set(key, value);
    setParams(next, { replace: true });
  };

  const goTo = (target: string) => {
    const next = new URLSearchParams(params);
    next.set('dag', target);
    // Den valgte dagen hører til forrige periode og ville ellers blitt hengende igjen.
    next.delete('dato');
    setParams(next, { replace: true });
  };

  const changeView = (nextView: ViewKind) => {
    const next = new URLSearchParams(params);
    next.set('visning', nextView);
    next.set('dag', anchor);
    setParams(next, { replace: true });
  };

  useEffect(() => {
    if (range.data) setAnnouncement(`${title}, ${range.data.length} dager`);
  }, [title, range.data]);

  const days = new Map((range.data ?? []).map((d) => [d.date, d]));
  const selectedDay = selected ? days.get(selected) : undefined;
  const todayEntry = days.get(today);
  const nowIsOpen = todayEntry?.isOpen ?? false;
  const loggedIn = session.data?.loggedIn ?? false;

  // Fokus flyttes til panelets overskrift når en dag åpnes, slik at skjermlesere
  // og tastaturbrukere havner der innholdet faktisk er.
  const selectDay = (date: string) => {
    setParam('dato', date);
    requestAnimationFrame(() => headingRef.current?.focus());
  };

  const closePanel = () => {
    const date = selected;
    setParam('dato', null);
    if (date) {
      requestAnimationFrame(() => {
        document.querySelector<HTMLElement>(`[data-date="${date}"]`)?.focus();
      });
    }
  };

  useEffect(() => {
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && selected) closePanel();
    };
    window.addEventListener('keydown', onEscape);
    return () => window.removeEventListener('keydown', onEscape);
  });

  const noGroup = range.data?.every((d) => Boolean(d.warningMessage)) ?? false;

  // Månedsvelgeren speiler ankeret, slik at den viser riktig måned også når
  // brukeren har navigert med pilknappene eller PageUp/PageDown.
  const { monthpickerProps, inputProps: monthInputProps, setSelected: setPickedMonth } =
    useMonthpicker({
      defaultSelected: isoToDate(firstOfMonth(monthOf(anchor))),
      onMonthChange: (picked) => {
        if (picked) goTo(firstOfMonth(monthOf(dateToIso(picked))));
      },
    });

  const anchorMonth = monthOf(anchor);
  useEffect(() => {
    // setPickedMonth er ustabil mellom renders og hører ikke hjemme i avhengighetene.
    setPickedMonth(isoToDate(firstOfMonth(anchorMonth)));
  }, [anchorMonth]);

  return (
    <div className="oh-page">
      <AppLink to="/tjenester" className="oh-back">
        <ArrowLeftIcon aria-hidden /> Tilbake til alle tjenester
      </AppLink>

      <div className="oh-page__head">
        <div>
          <Heading level="1" size="xlarge">
            {service.data?.name ?? <Skeleton width="18rem" />}
          </Heading>
          {service.data && (
            <BodyShort textColor="subtle">
              {service.data.type === 'TJENESTE' ? 'Tjeneste' : 'Komponent'} · team{' '}
              {service.data.team}
            </BodyShort>
          )}
        </div>
        <div className="oh-page__head-actions">
          <Select
            label="Bytt tjeneste"
            size="small"
            value={serviceId}
            onChange={(e) => navigate(`/t/${e.target.value}?${params.toString()}`)}
            className="oh-page__switcher"
          >
            {(services.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
          <AppLink to={`/sammenlign?dato=${selected ?? today}`}>
            Sammenlign tjenester
          </AppLink>
        </div>
      </div>

      {noGroup && service.data && (
        <Alert variant="warning">
          {service.data.name} har ingen åpningstidsgruppe ennå. Vi kan derfor ikke si når tjenesten
          er åpen.
        </Alert>
      )}

      <Tabs value={view} onChange={(v) => changeView(v as ViewKind)}>
        <Tabs.List>
          <Tabs.Tab value="maned" label="Måned" />
          <Tabs.Tab value="uke" label="Uke" />
          <Tabs.Tab value="aar" label="År" />
        </Tabs.List>
      </Tabs>

      <div className="oh-nav">
        <div className="oh-nav__buttons">
          <Button
            variant="secondary"
            size="small"
            icon={<ChevronLeftIcon aria-hidden />}
            onClick={() => goTo(shiftAnchor(view, anchor, -1))}
          >
            Forrige {unit}
          </Button>
          <Button
            variant="secondary"
            size="small"
            iconPosition="right"
            icon={<ChevronRightIcon aria-hidden />}
            onClick={() => goTo(shiftAnchor(view, anchor, 1))}
          >
            Neste {unit}
          </Button>
          <Button
            variant="tertiary"
            size="small"
            onClick={() => {
              goTo(today);
              selectDay(today);
            }}
          >
            I dag
          </Button>
        </div>
        <div className="oh-nav__title">
          <Heading level="2" size="small" aria-live="polite">
            {title}
          </Heading>
          {view === 'maned' && (
            <MonthPicker {...monthpickerProps} dropdownCaption>
              <MonthPicker.Input
                {...monthInputProps}
                label="Velg måned"
                hideLabel
                size="small"
                className="oh-nav__monthpicker"
              />
            </MonthPicker>
          )}
        </div>
      </div>

      <span className="oh-sr-only" role="status" aria-live="polite">
        {announcement}
      </span>

      {range.isError && (
        <ErrorState
          message={`Vi klarte ikke å hente åpningstidene for ${title.toLowerCase()}.`}
          onRetry={() => range.refetch()}
        />
      )}

      {range.isPending && !range.isError && <DelayedLoader />}

      {!range.isError && (
        <div className="oh-calendar">
          {isMobile ? (
            <AgendaList
              days={range.data ?? []}
              today={today}
              nowIsOpen={nowIsOpen}
              onSelect={selectDay}
            />
          ) : view === 'uke' ? (
            <WeekView
              monday={startOfWeek(anchor)}
              days={days}
              today={today}
              nowIsOpen={nowIsOpen}
              selected={selected}
              loggedIn={loggedIn}
              onSelect={selectDay}
            />
          ) : view === 'aar' ? (
            <YearView
              year={yearOf(anchor)}
              days={days}
              today={today}
              selected={selected}
              onSelect={selectDay}
            />
          ) : (
            <MonthGrid
              month={monthOf(anchor)}
              days={days}
              today={today}
              nowIsOpen={nowIsOpen}
              selected={selected}
              onSelect={selectDay}
              onNavigateMonth={(_month, focusDate) => goTo(focusDate)}
            />
          )}

          {selectedDay && !isMobile && (
            <DayDetailPanel
              day={selectedDay}
              loggedIn={loggedIn}
              onClose={closePanel}
              headingRef={headingRef}
            />
          )}
        </div>
      )}

      <Legend hasMasked={[...days.values()].some((d) => d.masked)} />

      {selectedDay && isMobile && (
        <Modal open onClose={closePanel} header={{ heading: 'Detaljer for dagen' }}>
          <Modal.Body>
            <DayDetailPanel
              day={selectedDay}
              loggedIn={loggedIn}
              onClose={closePanel}
              headingRef={headingRef}
            />
          </Modal.Body>
        </Modal>
      )}

      {range.isFetching && !range.isPending && (
        <div className="oh-page__refetch" aria-hidden>
          <Loader size="small" />
        </div>
      )}
    </div>
  );
}

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 720px)').matches,
  );
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 720px)');
    const listener = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mq.addEventListener('change', listener);
    return () => mq.removeEventListener('change', listener);
  }, []);
  return isMobile;
}
