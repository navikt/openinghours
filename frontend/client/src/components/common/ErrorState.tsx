import { Alert, BodyLong, Button, Heading } from '@navikt/ds-react';

interface Props {
  message: string;
  onRetry?: () => void;
}

/**
 * API-ets tekniske melding vises aldri direkte — den logges.
 * Brukeren får en handling, ikke en stacktrace.
 */
export function ErrorState({ message, onRetry }: Props) {
  return (
    <Alert variant="error">
      <BodyLong spacing>
        {message} Prøv igjen om litt. Hvis det fortsetter, meld saken til teamet som eier tjenesten.
      </BodyLong>
      {onRetry && (
        <Button variant="secondary" size="small" onClick={onRetry}>
          Prøv igjen
        </Button>
      )}
    </Alert>
  );
}

interface EmptyProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

/** Tomme tilstander navngir alltid årsaken og tilbyr veien tilbake. Ingen illustrasjon. */
export function EmptyState({ title, description, actionLabel, onAction }: EmptyProps) {
  return (
    <div>
      <Heading level="2" size="small" spacing>
        {title}
      </Heading>
      <BodyLong spacing>{description}</BodyLong>
      {actionLabel && onAction && (
        <Button variant="secondary" size="small" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}
