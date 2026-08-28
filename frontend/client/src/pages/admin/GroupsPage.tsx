import { useState } from 'react';
import {
  BodyShort,
  Button,
  HStack,
  Heading,
  Modal,
  Table,
  TextField,
  VStack,
} from '@navikt/ds-react';
import { useCreateGroup, useGroups, useRules, useServiceGroupLinks } from '../../hooks/admin';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { EmptyState, ErrorState } from '../../components/common/ErrorState';
import { buildRegistry, buildTree } from '../../lib/tree';
import { AppLink } from '../../components/common/AppLink';

export function GroupsPage() {
  const groups = useGroups();
  const rules = useRules();
  const links = useServiceGroupLinks(groups.data);
  const create = useCreateGroup();
  const [newOpen, setNewOpen] = useState(false);
  const [name, setName] = useState('');

  if (groups.isPending || rules.isPending) return <DelayedLoader />;
  if (groups.isError) {
    return <ErrorState message="Vi klarte ikke å hente gruppene." onRetry={() => groups.refetch()} />;
  }

  const registry = buildRegistry(groups.data, rules.data ?? []);

  // Hvor mange tjenester bruker hver gruppe? Vi snur oppslaget fra koblingene.
  const serviceCount = new Map<string, number>();
  for (const group of links.byService.values()) {
    serviceCount.set(group.id, (serviceCount.get(group.id) ?? 0) + 1);
  }

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
        <Table size="small">
          <Table.Header>
            <Table.Row>
              <Table.HeaderCell scope="col">Gruppe</Table.HeaderCell>
              <Table.HeaderCell scope="col">Medlemmer</Table.HeaderCell>
              <Table.HeaderCell scope="col">Brukt av</Table.HeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {groups.data.map((group) => {
              const members = buildTree(group.id, registry, 1).length;
              const services = serviceCount.get(group.id) ?? 0;
              return (
                <Table.Row key={group.id}>
                  <Table.DataCell>
                    <AppLink to={`/admin/grupper/${group.id}`}>{group.name}</AppLink>
                  </Table.DataCell>
                  <Table.DataCell>
                    {members === 0 ? (
                      <span className="oh-warning-text">Tom</span>
                    ) : (
                      `${members} ${members === 1 ? 'medlem' : 'medlemmer'}`
                    )}
                  </Table.DataCell>
                  <Table.DataCell>
                    {services === 0
                      ? 'Ingen tjenester'
                      : `${services} ${services === 1 ? 'tjeneste' : 'tjenester'}`}
                  </Table.DataCell>
                </Table.Row>
              );
            })}
          </Table.Body>
        </Table>
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
