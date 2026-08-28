import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BodyShort, Button, HStack, Heading, Table, Tag, VStack } from '@navikt/ds-react';
import { useGroups, useServiceGroupLinks } from '../../hooks/admin';
import { useServices } from '../../hooks/queries';
import { ServiceFormModal } from '../../components/admin/ServiceFormModal';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { ErrorState } from '../../components/common/ErrorState';
import type { Service } from '../../api/types';

export function ServicesPage() {
  const services = useServices();
  const groups = useGroups();
  const links = useServiceGroupLinks(groups.data);
  const [editing, setEditing] = useState<Service | 'new' | null>(null);

  if (services.isPending || groups.isPending) return <DelayedLoader />;
  if (services.isError) {
    return (
      <ErrorState message="Vi klarte ikke å hente tjenestene." onRetry={() => services.refetch()} />
    );
  }

  // Tjenester uten gruppe sorteres øverst. Listens viktigste jobb er å gjøre
  // hullene synlige uten at du må lete etter dem.
  const sorted = [...services.data].sort((a, b) => {
    const aHas = links.byService.has(a.id);
    const bHas = links.byService.has(b.id);
    if (aHas !== bHas) return aHas ? 1 : -1;
    return a.name.localeCompare(b.name, 'nb');
  });

  return (
    <VStack gap="5">
      <HStack justify="space-between" align="end" wrap gap="4">
        <div>
          <Heading level="1" size="large">
            Tjenester
          </Heading>
          <BodyShort textColor="subtle">
            {services.data.length} {services.data.length === 1 ? 'tjeneste' : 'tjenester'} · gruppen
            bestemmer alt kalenderen viser
          </BodyShort>
        </div>
        <Button onClick={() => setEditing('new')}>Ny tjeneste</Button>
      </HStack>

      <Table size="small">
        <Table.Header>
          <Table.Row>
            <Table.HeaderCell scope="col">Tjeneste</Table.HeaderCell>
            <Table.HeaderCell scope="col">Type</Table.HeaderCell>
            <Table.HeaderCell scope="col">Åpningstidsgruppe</Table.HeaderCell>
            <Table.HeaderCell scope="col">
              <span className="oh-sr-only">Handlinger</span>
            </Table.HeaderCell>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {sorted.map((service) => {
            const group = links.byService.get(service.id);
            return (
              <Table.Row key={service.id}>
                <Table.DataCell>
                  <Link to={`/t/${service.id}`}>{service.name}</Link>
                  <BodyShort size="small" textColor="subtle">
                    {service.team}
                  </BodyShort>
                </Table.DataCell>
                <Table.DataCell>
                  <Tag size="small" variant="neutral">
                    {service.type === 'TJENESTE' ? 'Tjeneste' : 'Komponent'}
                  </Tag>
                </Table.DataCell>
                <Table.DataCell>
                  {group ? (
                    <Link to={`/admin/grupper/${group.id}`}>{group.name}</Link>
                  ) : links.isPending ? (
                    <BodyShort size="small" textColor="subtle">
                      Henter …
                    </BodyShort>
                  ) : (
                    <span className="oh-warning-text">Ingen gruppe</span>
                  )}
                </Table.DataCell>
                <Table.DataCell>
                  <Button variant="tertiary" size="small" onClick={() => setEditing(service)}>
                    Rediger
                  </Button>
                </Table.DataCell>
              </Table.Row>
            );
          })}
        </Table.Body>
      </Table>

      {editing !== null && (
        <ServiceFormModal
          service={editing === 'new' ? null : editing}
          groups={groups.data ?? []}
          currentGroupId={editing === 'new' ? null : (links.byService.get(editing.id)?.id ?? null)}
          onClose={() => setEditing(null)}
        />
      )}
    </VStack>
  );
}
