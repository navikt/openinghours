import { useState } from 'react';
import { BodyShort, Button, Checkbox, Modal, Search, VStack } from '@navikt/ds-react';
import type { OhGroup, Rule } from '../../api/types';
import { formatRule } from '../../lib/rule';
import { wouldCycle, type Registry } from '../../lib/tree';

interface Props {
  open: boolean;
  kind: 'rule' | 'group';
  /** Gruppen medlemmet skal legges inn i. */
  parentId: string;
  rules: Rule[];
  groups: OhGroup[];
  registry: Registry;
  /** Id-er som allerede er medlem — avkrysset og deaktivert. */
  existing: Set<string>;
  onClose: () => void;
  onAdd: (ids: string[]) => void;
}

/**
 * Velg regler eller undergrupper å legge til.
 *
 * Nye medlemmer legges **nederst**. Å legge dem øverst ville endret prioriteten
 * til alt annet i gruppen uten at noen ba om det.
 */
export function AddMemberModal({
  open,
  kind,
  parentId,
  rules,
  groups,
  registry,
  existing,
  onClose,
  onAdd,
}: Props) {
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<string[]>([]);

  const needle = search.trim().toLowerCase();
  const candidates =
    kind === 'rule'
      ? rules
          .filter((r) => !needle || `${r.name} ${r.rule}`.toLowerCase().includes(needle))
          .map((r) => ({ id: r.id, name: r.name, detail: formatRule(r.rule), blocked: false }))
      : groups
          .filter((g) => !needle || g.name.toLowerCase().includes(needle))
          .map((g) => ({
            id: g.id,
            name: g.name,
            detail: `${(g.ruleGroupIds ?? []).length} medlemmer`,
            // Sirkelen sperres her, ikke i API-svaret: valget skal være
            // utilgjengelig framfor å feile etter at du trykket.
            blocked: wouldCycle(parentId, g.id, registry),
          }));

  function close() {
    setSelected([]);
    setSearch('');
    onClose();
  }

  return (
    <Modal
      open={open}
      onClose={close}
      width="medium"
      header={{ heading: kind === 'rule' ? 'Legg til regel' : 'Legg til undergruppe' }}
    >
      <Modal.Body>
        <VStack gap="4">
          <Search
            label="Søk"
            size="small"
            variant="simple"
            value={search}
            onChange={setSearch}
            onClear={() => setSearch('')}
          />
          <BodyShort size="small" textColor="subtle">
            Nye medlemmer legges nederst i gruppen, så prioriteten til de andre er uendret.
          </BodyShort>
          <VStack gap="1">
            {candidates.length === 0 && (
              <BodyShort size="small">Ingen treff. Prøv et annet søkeord.</BodyShort>
            )}
            {candidates.map((candidate) => {
              const already = existing.has(candidate.id);
              return (
                <Checkbox
                  key={candidate.id}
                  size="small"
                  checked={already || selected.includes(candidate.id)}
                  disabled={already || candidate.blocked}
                  description={
                    candidate.blocked
                      ? 'Kan ikke legges til: det ville laget en sirkel'
                      : already
                        ? 'Er allerede i gruppen'
                        : candidate.detail
                  }
                  onChange={(e) =>
                    setSelected((prev) =>
                      e.target.checked
                        ? [...prev, candidate.id]
                        : prev.filter((id) => id !== candidate.id),
                    )
                  }
                >
                  {candidate.name}
                </Checkbox>
              );
            })}
          </VStack>
        </VStack>
      </Modal.Body>
      <Modal.Footer>
        <Button
          disabled={selected.length === 0}
          onClick={() => {
            onAdd(selected);
            close();
          }}
        >
          Legg til {selected.length > 0 && `(${selected.length})`}
        </Button>
        <Button variant="tertiary" onClick={close}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
