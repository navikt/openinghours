import { useMemo, useState } from 'react';
import { BodyShort, Chips, Heading, VStack } from '@navikt/ds-react';
import { addDays, formatDateLong, isoWeekday, todayIso } from '../../lib/date';
import { previewRule } from '../../lib/evaluate';
import { StatusBadge } from '../calendar/StatusBadge';
import './RulePreview.css';

/** Neste forekomst av en gitt ukedag, i dag medregnet. */
function nextWeekday(from: string, weekday: number): string {
  let date = from;
  for (let i = 0; i < 7; i += 1) {
    if (isoWeekday(date) === weekday) return date;
    date = addDays(date, 1);
  }
  return from;
}

/** 17. mai — neste gang den kommer. Den vanligste røde dagen å teste mot. */
function nextConstitutionDay(today: string): string {
  const year = Number(today.slice(0, 4));
  const thisYear = `${year}-05-17`;
  return today <= thisYear ? thisYear : `${year + 1}-05-17`;
}

/**
 * Fire forhåndsvalgte datoer dekker de typiske tilfellene: i dag, en hverdag,
 * en helgedag og en helligdag.
 *
 * «Traff ikke» er en gyldig og nyttig visning — den forklarer hvilket felt som
 * utelukket datoen, og er ofte akkurat det brukeren trenger å se.
 */
export function RulePreview({ expr, invalid = false }: { expr: string; invalid?: boolean }) {
  const today = todayIso();
  const dates = useMemo(
    () => [
      { label: 'I dag', date: today },
      { label: 'Fredag', date: nextWeekday(today, 5) },
      { label: 'Lørdag', date: nextWeekday(today, 6) },
      { label: '17. mai', date: nextConstitutionDay(today) },
    ],
    [today],
  );
  const [selected, setSelected] = useState(0);
  const active = dates[selected] ?? dates[0];
  const result = previewRule(expr, active.date);

  const hours = result.closed
    ? 'Stengt'
    : result.allDay
      ? 'Døgnåpent'
      : result.hours
        ? `${result.hours.open}–${result.hours.close}`
        : null;

  return (
    <section className="oh-preview" aria-label="Forhåndsvisning">
      <Heading level="2" size="small" spacing>
        Forhåndsvisning
      </Heading>
      <BodyShort size="small" textColor="subtle" spacing>
        Beregnet her i nettleseren, med samme logikk som backend. Hva brukeren faktisk ser avhenger
        av rekkefølgen i gruppen — det testes på gruppesiden.
      </BodyShort>

      <Chips>
        {dates.map((entry, index) => (
          <Chips.Toggle
            key={entry.label}
            selected={selected === index}
            onClick={() => setSelected(index)}
          >
            {entry.label}
          </Chips.Toggle>
        ))}
      </Chips>

      <div className="oh-preview__result" aria-live="polite">
        <BodyShort size="small" weight="semibold">
          {formatDateLong(active.date)}
        </BodyShort>
        <VStack gap="2">
          {invalid ? (
            <BodyShort size="small" textColor="subtle">
              Rett opp uttrykket for å se forhåndsvisningen.
            </BodyShort>
          ) : result.matches ? (
            <>
              <StatusBadge
                kind={result.closed ? 'closed' : 'open'}
                label={result.closed ? 'Stengt' : 'Åpent'}
                size="small"
              />
              {hours && !result.closed && <BodyShort size="small">{hours}</BodyShort>}
            </>
          ) : (
            <BodyShort size="small" weight="semibold">
              Traff ikke
            </BodyShort>
          )}
          <BodyShort size="small" textColor="subtle">
            {invalid ? '' : result.reason}
          </BodyShort>
        </VStack>
      </div>
    </section>
  );
}
