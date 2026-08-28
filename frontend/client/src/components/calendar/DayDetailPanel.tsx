import { Alert, BodyLong, BodyShort, Box, CopyButton, Heading, Label } from '@navikt/ds-react';
import { XMarkIcon } from '@navikt/aksel-icons';
import { Button } from '@navikt/ds-react';
import type { QueryResponse } from '../../api/types';
import { formatDateLong, weekdayName } from '../../lib/date';
import { formatRule } from '../../lib/rule';
import { deriveStatus } from '../../lib/status';
import { StatusBadge } from './StatusBadge';
import './DayDetailPanel.css';

interface Props {
  day: QueryResponse;
  loggedIn: boolean;
  onClose: () => void;
  headingRef?: React.Ref<HTMLHeadingElement>;
}

/**
 * Detaljer for valgt dag.
 *
 * Regelnavn og regeluttrykk vises kun til innloggede — de er intern informasjon
 * om hvordan åpningstiden er satt opp.
 */
export function DayDetailPanel({ day, loggedIn, onClose, headingRef }: Props) {
  const status = deriveStatus(day);

  return (
    <Box className="oh-panel" padding="6" borderRadius="large" borderWidth="1" background="surface-default">
      <div className="oh-panel__head">
        <Label size="small" textColor="subtle">
          Detaljer for dagen
        </Label>
        <Button
          variant="tertiary-neutral"
          size="small"
          icon={<XMarkIcon aria-hidden />}
          onClick={onClose}
          title="Lukk detaljer"
        />
      </div>

      <Heading level="2" size="small" ref={headingRef} tabIndex={-1}>
        {formatDateLong(day.date)} · {weekdayName(day.date)}
      </Heading>

      <StatusBadge kind={status.kind} label={status.label} />
      {status.detail && (
        <BodyShort size="small" textColor="subtle">
          {status.detail}
        </BodyShort>
      )}

      {day.warningMessage && (
        <Alert variant="warning" size="small" inline={false}>
          Vi kan ikke si når tjenesten er åpen denne dagen. Ingen åpningstidsregel treffer datoen.
        </Alert>
      )}

      {day.masked && (
        <Alert variant="info" size="small">
          Åpningstiden denne dagen gjelder kun Nav-ansatte. Logg inn for å se den.
        </Alert>
      )}

      {(day.displayHeader || day.displayText) && (
        <div className="oh-panel__message">
          {day.displayHeader && (
            <BodyShort weight="semibold">{day.displayHeader}</BodyShort>
          )}
          {day.displayText && <BodyLong size="small">{day.displayText}</BodyLong>}
        </div>
      )}

      {loggedIn && day.matchedRule && (
        <div className="oh-panel__rule">
          <Label size="small" textColor="subtle">
            Regel som traff
          </Label>
          <BodyShort weight="semibold">{day.matchedRule.name}</BodyShort>
          <BodyShort size="small">{formatRule(day.matchedRule.rule)}</BodyShort>
          <div className="oh-panel__expression">
            <code>{day.matchedRule.rule}</code>
            <CopyButton copyText={day.matchedRule.rule} size="small" title="Kopier regeluttrykket" />
          </div>
          <BodyShort size="small" textColor="subtle">
            Rød dag: {day.redDay ? 'ja' : 'nei'}
            {status.holiday ? ` (${status.holiday})` : ''}
          </BodyShort>
        </div>
      )}
    </Box>
  );
}
