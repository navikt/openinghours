import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  BodyShort,
  Button,
  Chips,
  HStack,
  Heading,
  Modal,
  Tag,
  TextField,
  VStack,
} from '@navikt/ds-react';
import { ChevronRightIcon, ClockIcon, FolderIcon, LayersIcon } from '@navikt/aksel-icons';
import type { OhGroup } from '../../api/types';
import { useServices } from '../../hooks/queries';
import { useCreateGroup, useGroups, useRules, useServiceGroupLinks } from '../../hooks/admin';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { EmptyState, ErrorState } from '../../components/common/ErrorState';
import { buildRegistry, buildTree, type Registry } from '../../lib/tree';
import './GroupsPage.css';

export function GroupsPage() {
  const groups = useGroups();
  const rules = useRules();
  const links = useServiceGroupLinks(groups.data);
  const create = useCreateGroup();
  const [newOpen, setNewOpen] = useState(false);
  const [name, setName] = useState('');
  const [filter, setFilter] = useState<Filter>('alle');
  const services = useServices();

  if (groups.isPending || rules.isPending) return <DelayedLoader />;
  if (groups.isError) {
    return <ErrorState message="Vi klarte ikke å hente gruppene." onRetry={() => groups.refetch()} />;
  }

  const registry = buildRegistry(groups.data, rules.data ?? []);

  // Hvilke tjenester bruker hver gruppe? Vi snur oppslaget fra koblingene.
  const serviceName = new Map((services.data ?? []).map((s) => [s.id, s.name]));
  const usedBy = new Map<string, string[]>();
  for (const [serviceId, group] of links.byService) {
    const names = usedBy.get(group.id) ?? [];
    names.push(serviceName.get(serviceId) ?? 'Ukjent tjeneste');
    usedBy.set(group.id, names);
  }

  const cards = groups.data.map((group) =>
    describeGroup(group, registry, usedBy.get(group.id) ?? [], links.isPending),
  );
  const counts: Record<Filter, number> = {
    alle: cards.length,
    brukt: cards.filter((c) => c.status === 'brukt').length,
    ubrukt: cards.filter((c) => c.status === 'ubrukt').length,
    tom: cards.filter((c) => c.status === 'tom').length,
  };
  const visible = filter === 'alle' ? cards : cards.filter((c) => c.status === filter);

  return (
    <VStack gap="5">
      <HStack justify="space-between" align="end" wrap gap="4">
        <div>
          <Heading level="1" size="large">
            Åpningstidsgrupper
          </Heading>
          <BodyShort textColor="subtle">
            {groups.data.length} {groups.data.length === 1 ? 'gruppe' : 'grupper'} · rekkefølgen i
            gruppen bestemmer hvilken regel som vinner
          </BodyShort>
        </div>
        <Button onClick={() => setNewOpen(true)}>Ny gruppe</Button>
      </HStack>

      {groups.data.length === 0 ? (
        <EmptyState
          title="Ingen grupper ennå"
          description="En gruppe samler reglene som gjelder for en tjeneste, i prioritert rekkefølge."
          actionLabel="Lag den første gruppen"
          onAction={() => setNewOpen(true)}
        />
      ) : (
        <>
          <Chips aria-label="Filtrer grupper">
            {FILTERS.map(({ value, label }) => (
              <Chips.Toggle
                key={value}
                selected={filter === value}
                checkmark={false}
                onClick={() => setFilter(value)}
              >
                {`${label} (${counts[value]})`}
              </Chips.Toggle>
            ))}
          </Chips>

          {visible.length === 0 ? (
            <BodyShort textColor="subtle">Ingen grupper passer filteret.</BodyShort>
          ) : (
            <ul className="oh-groupcards">
              {visible.map((card) => (
                <li key={card.id}>
                  <GroupCard card={card} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      <Modal
        open={newOpen}
        onClose={() => setNewOpen(false)}
        header={{ heading: 'Ny åpningstidsgruppe' }}
        width="small"
      >
        <Modal.Body>
          <TextField
            label="Navn på gruppen"
            description="Beskriv hvem gruppen gjelder for, for eksempel «Selvbetjening ordinær»."
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <BodyShort size="small" textColor="subtle" spacing>
            Gruppen opprettes tom. Du legger til regler etterpå.
          </BodyShort>
        </Modal.Body>
        <Modal.Footer>
          <Button
            loading={create.isPending}
            disabled={name.trim() === ''}
            onClick={async () => {
              await create.mutateAsync({ name: name.trim() });
              setName('');
              setNewOpen(false);
            }}
          >
            Opprett gruppen
          </Button>
          <Button variant="tertiary" onClick={() => setNewOpen(false)}>
            Avbryt
          </Button>
        </Modal.Footer>
      </Modal>
    </VStack>
  );
}

type Status = 'brukt' | 'ubrukt' | 'tom' | 'ukjent';
type Filter = 'alle' | Exclude<Status, 'ukjent'>;

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'alle', label: 'Alle' },
  { value: 'brukt', label: 'I bruk' },
  { value: 'ubrukt', label: 'Ikke i bruk' },
  { value: 'tom', label: 'Tomme' },
];

const PREVIEW = 3;

interface GroupCardData {
  id: string;
  name: string;
  rules: number;
  subgroups: number;
  preview: string[];
  more: number;
  services: string[];
  status: Status;
}

function describeGroup(
  group: OhGroup,
  registry: Registry,
  services: string[],
  linksPending: boolean,
): GroupCardData {
  const members = buildTree(group.id, registry, 1);
  const status: Status =
    members.length === 0 ? 'tom' : linksPending ? 'ukjent' : services.length > 0 ? 'brukt' : 'ubrukt';
  return {
    id: group.id,
    name: group.name,
    rules: members.filter((m) => m.kind === 'rule').length,
    subgroups: members.filter((m) => m.kind === 'group').length,
    preview: members.slice(0, PREVIEW).map((m) => m.name),
    more: Math.max(members.length - PREVIEW, 0),
    services: [...services].sort((a, b) => a.localeCompare(b, 'nb')),
    status,
  };
}

const STATUS_TAG: Record<Exclude<Status, 'ukjent'>, { label: string; variant: 'success' | 'neutral' | 'warning' }> = {
  brukt: { label: 'I bruk', variant: 'success' },
  ubrukt: { label: 'Ikke i bruk', variant: 'neutral' },
  tom: { label: 'Tom', variant: 'warning' },
};

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

function GroupCard({ card }: { card: GroupCardData }) {
  const tag = card.status === 'ukjent' ? null : STATUS_TAG[card.status];
  return (
    <article className={`oh-groupcard oh-groupcard--${card.status}`}>
      <span className="oh-groupcard__icon" aria-hidden>
        <FolderIcon />
      </span>

      <div className="oh-groupcard__body">
        <div className="oh-groupcard__top">
          <Heading level="2" size="xsmall">
            <RouterLink className="oh-groupcard__link" to={`/admin/grupper/${card.id}`}>
              {card.name}
            </RouterLink>
          </Heading>
          {tag && (
            <Tag size="xsmall" variant={tag.variant}>
              {tag.label}
            </Tag>
          )}
        </div>

        {card.status === 'tom' ? (
          <BodyShort size="small" textColor="subtle">
            Ingen regler ennå — åpne gruppen for å legge til.
          </BodyShort>
        ) : (
          <>
            <div className="oh-groupcard__stats">
              {card.rules > 0 && (
                <span>
                  <ClockIcon aria-hidden /> {plural(card.rules, 'regel', 'regler')}
                </span>
              )}
              {card.subgroups > 0 && (
                <span>
                  <LayersIcon aria-hidden /> {plural(card.subgroups, 'undergruppe', 'undergrupper')}
                </span>
              )}
            </div>
            <ul className="oh-groupcard__members" aria-label="Første medlemmer i prioritert rekkefølge">
              {card.preview.map((member, index) => (
                <li key={`${member}-${index}`}>{member}</li>
              ))}
              {card.more > 0 && <li className="oh-groupcard__more">+{card.more} til</li>}
            </ul>
          </>
        )}

        <BodyShort size="small" className="oh-groupcard__usage">
          {card.status === 'ukjent'
            ? 'Henter tjenester …'
            : card.services.length === 0
              ? 'Ingen tjenester bruker gruppen'
              : `Brukt av ${card.services.slice(0, 3).join(', ')}${
                  card.services.length > 3 ? ` og ${card.services.length - 3} til` : ''
                }`}
        </BodyShort>
      </div>

      <ChevronRightIcon className="oh-groupcard__chevron" aria-hidden />
    </article>
  );
}
