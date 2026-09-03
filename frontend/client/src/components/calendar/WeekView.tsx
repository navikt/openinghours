import { BodyShort, Detail } from '@navikt/ds-react';
import type { QueryResponse } from '../../api/types';
import { deriveStatus, statusAriaLabel } from '../../lib/status';
import { formatRule } from '../../lib/rule';
import { formatDateLong, weekDays, weekdayName } from '../../lib/date';
import { StatusBadge, NowIndicator, UnstableMark } from './StatusBadge';
import { OpeningBar, TimeAxis } from './OpeningBar';
import './WeekView.css';

interface Props {
  monday: string;
  days: Map<string, QueryResponse>;
  today: string;
  nowIsOpen: boolean;
  selected: string | null;
  loggedIn: boolean;
  onSelect: (date: string) => void;
}

/**
 * Ukevisning — én rad per dag på en felles tidsakse.
 *
 * Rad-geometrien er fast (dagkolonne / spor / regelkolonne), slik at sporene
 * står loddrett under hverandre og kan sammenlignes på tvers av dagene.
 * Regelkolonnen vises kun for innloggede, av samme grunn som i detaljpanelet.
 */
export function WeekView({
  monday,
  days,
  today,
  nowIsOpen,
  selected,
  loggedIn,
  onSelect,
}: Props) {
  const dates = weekDays(monday);

  return (
    <div className={`oh-week${loggedIn ? ' oh-week--with-rules' : ''}`}>
      <div className="oh-week__head" aria-hidden>
        <div className="oh-week__day" />
        <div className="oh-week__track">
          <TimeAxis />
        </div>
        {loggedIn && <div className="oh-week__rule">Regel som traff</div>}
      </div>

      <ul className="oh-week__rows">
        {dates.map((date) => {
          const day = days.get(date);
          const status = day ? deriveStatus(day) : null;
          const isToday = date === today;

          return (
            <li key={date}>
              <button
                type="button"
                data-date={date}
                className={[
                  'oh-week__row',
                  isToday ? 'oh-week__row--today' : '',
                  selected === date ? 'oh-week__row--selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                aria-current={isToday ? 'date' : undefined}
                aria-label={day && status ? statusAriaLabel(day, status) : formatDateLong(date)}
                onClick={() => onSelect(date)}
              >
                <span className="oh-week__day">
                  <BodyShort weight="semibold" as="span">
                    {capitalize(weekdayName(date))}
                  </BodyShort>
                  <Detail as="span" textColor="subtle">
                    {Number(date.slice(8, 10))}.{Number(date.slice(5, 7))}.
                  </Detail>
                  {isToday && <NowIndicator isOpen={nowIsOpen} />}
                </span>

                <span className="oh-week__track">
                  {status && (
                    <>
                      <OpeningBar
                        intervals={status.intervals}
                        allDay={status.allDay}
                        variant="track"
                        showTimes
                      />
                      <span className="oh-week__status">
                        <StatusBadge kind={status.kind} label={status.label} size="small" decorative />
                        {status.unstable && <UnstableMark />}
                        {status.detail && (
                          <Detail as="span" textColor="subtle">
                            {status.detail}
                          </Detail>
                        )}
                      </span>
                    </>
                  )}
                </span>

                {loggedIn && (
                  <span className="oh-week__rule">
                    {day?.matchedRule ? (
                      <>
                        <Detail as="span" weight="semibold">
                          {day.matchedRule.name}
                        </Detail>
                        <Detail as="span" textColor="subtle">
                          {formatRule(day.matchedRule.rule)}
                        </Detail>
                      </>
                    ) : (
                      <Detail as="span" textColor="subtle">
                        Ingen regel
                      </Detail>
                    )}
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
