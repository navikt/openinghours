import { BodyLong, BodyShort, Button, Heading, Modal, Tag, VStack } from '@navikt/ds-react';
import { Link } from 'react-router-dom';
import { ExclamationmarkTriangleIcon } from '@navikt/aksel-icons';
import './DeleteDialog.css';

export interface Conflict {
  id: string;
  kind: 'Gruppe' | 'Tjeneste';
  name: string;
  /** «Prioritet 4 av 7», «brukt av 5 tjenester» — kontekst, ikke gjentakelse av navnet. */
  context?: string;
  href?: string;
}

interface Props {
  open: boolean;
  /** «Regelen», «Gruppen», «Tjenesten» — brukes i knapp og overskrift. */
  noun: string;
  name: string;
  conflicts: Conflict[];
  /** Ekstra setning i den trygge tilstanden, f.eks. at reglene ikke slettes med gruppen. */
  note?: string;
  /** Hvor brukeren skal for å rydde opp. */
  resolveHref?: string;
  resolveLabel?: string;
  deleting?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

/**
 * Sletting av noe som kan være i bruk.
 *
 * Er det i bruk, er sletting **sperret** — ikke bare frarådet. Backend tilbyr en
 * `?confirm=true` som overstyrer vernet, men den sendes aldri herfra, og BFF-en
 * filtrerer den bort. Derfor finnes det ingen «slett likevel»-knapp.
 *
 * Overskriften stiller spørsmålet og navngir elementet. Konsekvensen står i
 * første avsnitt, ikke i knappeteksten — knappen sier hva som skjer.
 */
export function DeleteDialog({
  open,
  noun,
  name,
  conflicts,
  note,
  resolveHref,
  resolveLabel = 'Gå til gruppene',
  deleting = false,
  onClose,
  onConfirm,
}: Props) {
  const blocked = conflicts.length > 0;
  const lower = noun.toLowerCase();

  return (
    <Modal
      open={open}
      onClose={onClose}
      width="small"
      aria-label={blocked ? `${noun} kan ikke slettes ennå` : `Vil du slette ${name}?`}
    >
      <Modal.Header closeButton>
        <Heading level="1" size="small">
          {blocked ? (
            <span className="oh-delete__title">
              <ExclamationmarkTriangleIcon aria-hidden className="oh-delete__icon" />
              {noun} kan ikke slettes ennå
            </span>
          ) : (
            `Vil du slette «${name}»?`
          )}
        </Heading>
      </Modal.Header>

      <Modal.Body>
        {blocked ? (
          <VStack gap="4">
            <BodyLong>
              «{name}» brukes {describeUsage(conflicts)}. Sletter du {lower.replace(/en$/, 'en')},
              mister disse åpningstidene sine.
            </BodyLong>
            <div>
              <Heading level="2" size="xsmall" spacing>
                Brukes her
              </Heading>
              <ul className="oh-delete__list">
                {conflicts.map((conflict) => (
                  <li key={`${conflict.kind}-${conflict.id}`}>
                    <Tag size="small" variant="neutral">
                      {conflict.kind}
                    </Tag>
                    {conflict.href ? (
                      <Link to={conflict.href}>{conflict.name}</Link>
                    ) : (
                      <span>{conflict.name}</span>
                    )}
                    {conflict.context && (
                      <span className="oh-delete__context">{conflict.context}</span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
            <BodyShort>
              Fjern {lower} derfra først. Da kan du slette {lower} uten at noe endrer seg for
              brukerne.
            </BodyShort>
          </VStack>
        ) : (
          <VStack gap="2">
            <BodyLong>{note ?? `${noun} er ikke i bruk noe sted. Ingen tjenester påvirkes.`}</BodyLong>
            <BodyShort>Slettingen kan ikke angres.</BodyShort>
          </VStack>
        )}
      </Modal.Body>

      <Modal.Footer>
        {blocked ? (
          resolveHref && (
            <Button as={Link} to={resolveHref} onClick={onClose}>
              {resolveLabel}
            </Button>
          )
        ) : (
          <Button variant="danger" onClick={onConfirm} loading={deleting}>
            Slett {lower}
          </Button>
        )}
        {/* Avbryt er tertiær og har fokus når dialogen åpnes. */}
        <Button variant="tertiary" onClick={onClose} autoFocus>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

function describeUsage(conflicts: Conflict[]): string {
  const groups = conflicts.filter((c) => c.kind === 'Gruppe').length;
  const services = conflicts.filter((c) => c.kind === 'Tjeneste').length;
  const parts: string[] = [];
  if (groups > 0) parts.push(`i ${groups} ${groups === 1 ? 'gruppe' : 'grupper'}`);
  if (services > 0) parts.push(`av ${services} ${services === 1 ? 'tjeneste' : 'tjenester'}`);
  return parts.join(', og ');
}
