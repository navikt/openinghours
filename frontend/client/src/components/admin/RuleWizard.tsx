import {
  BodyShort,
  Button,
  Chips,
  CopyButton,
  HStack,
  Heading,
  Select,
  TextField,
  VStack,
} from '@navikt/ds-react';
import { CheckmarkCircleIcon, XMarkOctagonIcon } from '@navikt/aksel-icons';
import {
  RULE_PATTERNS,
  WEEKDAY_OPTIONS,
  buildRule,
  EMPTY_FIELDS,
  type RuleFields,
} from '../../lib/rulebuild';
import { formatRule } from '../../lib/rule';
import type { ValidationError } from '../../lib/validate';
import './RuleWizard.css';

interface Props {
  expr: string;
  /** `null` betyr at uttrykket er skrevet manuelt og feltene er låst. */
  fields: RuleFields | null;
  error: ValidationError | null;
  onFieldsChange: (fields: RuleFields) => void;
  onExprChange: (expr: string) => void;
  onReconnect: () => void;
}

/**
 * Veiviseren: fire felt som til sammen *er* regeluttrykket.
 *
 * Feltene og strengen er samme data. Endrer du et felt, skrives strengen på nytt;
 * endrer du strengen, fylles feltene på nytt så lenge uttrykket kan tolkes. Kan
 * det ikke tolkes, låses feltene framfor å vise noe som ikke stemmer.
 */
export function RuleWizard({
  expr,
  fields,
  error,
  onFieldsChange,
  onExprChange,
  onReconnect,
}: Props) {
  const locked = fields === null;
  const active = fields ?? EMPTY_FIELDS;
  const set = (patch: Partial<RuleFields>) => onFieldsChange({ ...active, ...patch });

  const matchingPattern = RULE_PATTERNS.find((p) => buildRule(p.fields) === expr);

  return (
    <div className="oh-wizard">
      <Heading level="2" size="small" spacing>
        Når gjelder regelen?
      </Heading>
      <BodyShort size="small" textColor="subtle" spacing>
        Fyll ut feltene du trenger. Et felt du lar stå tomt betyr «alle».
      </BodyShort>

      {locked && (
        <div className="oh-wizard__locked">
          <BodyShort size="small" spacing>
            Uttrykket er skrevet manuelt. Nullstill feltene for å redigere dem igjen.
          </BodyShort>
          <Button size="small" variant="secondary" onClick={onReconnect}>
            Nullstill feltene
          </Button>
        </div>
      )}

      <div className="oh-wizard__fields">
        <TextField
          label="Dato"
          size="small"
          description="17.05.2026 eller 17.05 hvert år"
          placeholder="Alle"
          value={active.date}
          disabled={locked}
          onChange={(e) => set({ date: e.target.value })}
        />
        <TextField
          label="Dag i måneden"
          size="small"
          description="1, 15, siste"
          placeholder="Alle"
          value={active.dayOfMonth}
          disabled={locked}
          onChange={(e) => set({ dayOfMonth: e.target.value })}
        />
        <Select
          label="Ukedag"
          size="small"
          description="Enkeltdag eller intervall"
          value={active.weekday}
          disabled={locked}
          onChange={(e) => set({ weekday: e.target.value })}
        >
          {WEEKDAY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
          {/* Uttrykk skrevet for hånd kan ha en kombinasjon som ikke er i listen.
              Vi legger den til framfor å stille valget om i stillhet. */}
          {!WEEKDAY_OPTIONS.some((o) => o.value === active.weekday) && (
            <option value={active.weekday}>{active.weekday}</option>
          )}
        </Select>
        <TextField
          label="Klokkeslett"
          size="small"
          description="08:00-15:30"
          placeholder="stengt"
          value={active.time}
          disabled={locked}
          onChange={(e) => set({ time: e.target.value })}
        />
      </div>

      <div className="oh-wizard__expr">
        <TextField
          label="Regeluttrykk"
          size="small"
          value={expr}
          error={error?.message}
          onChange={(e) => onExprChange(e.target.value)}
          className="oh-wizard__expr-input"
          aria-describedby="oh-wizard-receipt"
        />
        <CopyButton copyText={expr} size="small" title="Kopier uttrykket" />
      </div>

      {/* Kvitteringen er polite: den skal ikke avbryte deg mens du skriver. */}
      <div id="oh-wizard-receipt" aria-live="polite" className="oh-wizard__receipt">
        {error ? (
          <HStack gap="2" align="center" wrap={false}>
            <XMarkOctagonIcon aria-hidden className="oh-wizard__icon oh-wizard__icon--error" />
            <BodyShort size="small">{error.message}</BodyShort>
          </HStack>
        ) : (
          <HStack gap="2" align="center" wrap={false}>
            <CheckmarkCircleIcon aria-hidden className="oh-wizard__icon oh-wizard__icon--ok" />
            <BodyShort size="small">Uttrykket er gyldig — {formatRule(expr)}</BodyShort>
          </HStack>
        )}
      </div>

      <VStack gap="2" className="oh-wizard__patterns">
        <BodyShort size="small" weight="semibold">
          Eller start fra et vanlig mønster
        </BodyShort>
        <Chips>
          {RULE_PATTERNS.map((pattern) => (
            <Chips.Toggle
              key={pattern.id}
              selected={matchingPattern?.id === pattern.id}
              onClick={() => onFieldsChange(pattern.fields)}
            >
              {pattern.label}
            </Chips.Toggle>
          ))}
        </Chips>
        <BodyShort size="small" textColor="subtle">
          Trenger du en lunsjpause midt på dagen, lager du to regler i samme gruppe. Én regel har ett
          sammenhengende tidsrom.
        </BodyShort>
      </VStack>
    </div>
  );
}
