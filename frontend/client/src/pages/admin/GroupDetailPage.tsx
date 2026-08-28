import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, BodyShort, Button, HStack, Heading, VStack } from '@navikt/ds-react';
import { ApiError } from '../../api/client';
import {
  useDeleteGroup,
  useGroup,
  useGroupAssociations,
  useGroups,
  useRules,
  useUpdateGroup,
} from '../../hooks/admin';
import { AddMemberModal } from '../../components/admin/AddMemberModal';
import { DeleteDialog, type Conflict } from '../../components/admin/DeleteDialog';
import { GroupTestPanel } from '../../components/admin/GroupTestPanel';
import { GroupTree } from '../../components/admin/GroupTree';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { ErrorState } from '../../components/common/ErrorState';
import { buildRegistry, buildTree, moveItem, unreachableMembers } from '../../lib/tree';
import './GroupDetailPage.css';
import { AppLink } from '../../components/common/AppLink';

export function GroupDetailPage() {
  const { groupId = '' } = useParams();
  const navigate = useNavigate();

  const group = useGroup(groupId);
  const groups = useGroups();
  const rules = useRules();
  const associations = useGroupAssociations(groupId);
  const update = useUpdateGroup();
  const remove = useDeleteGroup();

  /** Lokal rekkefølge. Lagres først når du trykker «Lagre rekkefølgen». */
  const [members, setMembers] = useState<string[]>([]);
  const [addKind, setAddKind] = useState<'rule' | 'group' | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (group.data) setMembers(group.data.ruleGroupIds ?? []);
  }, [group.data?.id, group.data?.ruleGroupIds]);

  const registry = useMemo(
    () => buildRegistry(groups.data ?? [], rules.data ?? []),
    [groups.data, rules.data],
  );

  // Treet bygges fra den lokale rekkefølgen, ikke fra serverens, slik at
  // prioritetsnumrene skrives om umiddelbart etter hver flytting.
  const localRegistry = useMemo(() => {
    if (!group.data) return registry;
    const copy = new Map(registry.groups);
    copy.set(groupId, { ...group.data, ruleGroupIds: members });
    return { groups: copy, rules: registry.rules };
  }, [registry, group.data, groupId, members]);

  const tree = useMemo(
    () => (group.data ? buildTree(groupId, localRegistry) : []),
    [group.data, groupId, localRegistry],
  );
  const unreachable = useMemo(() => unreachableMembers(tree), [tree]);

  if (group.isPending || groups.isPending || rules.isPending) return <DelayedLoader />;
  if (group.isError || !group.data) {
    return <ErrorState message="Vi fant ikke gruppen. Den kan ha blitt slettet." />;
  }

  const dirty = JSON.stringify(members) !== JSON.stringify(group.data.ruleGroupIds ?? []);
  const serviceCount = associations.data?.services.length ?? 0;

  function move(index: number, direction: -1 | 1) {
    const target = index + direction;
    const node = tree[index];
    setMembers((prev) => moveItem(prev, index, target));
    setAnnouncement(`Flyttet ${node.name} til prioritet ${target + 1} av ${tree.length}.`);
  }

  const conflicts: Conflict[] = [
    ...(associations.data?.services ?? []).map((service) => ({
      id: service.id,
      kind: 'Tjeneste' as const,
      name: service.name,
      context: `team ${service.team}`,
      href: `/t/${service.id}`,
    })),
    ...(associations.data?.groups ?? []).map((parent) => ({
      id: parent.id,
      kind: 'Gruppe' as const,
      name: parent.name,
      context: 'inneholder denne gruppen',
      href: `/admin/grupper/${parent.id}`,
    })),
  ];

  return (
    <VStack gap="5">
      <div>
        <BodyShort size="small">
          <AppLink to="/admin/grupper">Tilbake til grupper</AppLink>
        </BodyShort>
        <Heading level="1" size="large">
          {group.data.name}
        </Heading>
        <BodyShort textColor="subtle">
          {members.length} {members.length === 1 ? 'medlem' : 'medlemmer'} · brukt av{' '}
          {serviceCount} {serviceCount === 1 ? 'tjeneste' : 'tjenester'}
        </BodyShort>
      </div>

      {saved && (
        <Alert variant="success" size="small" closeButton onClose={() => setSaved(false)}>
          Rekkefølgen er lagret.
        </Alert>
      )}

      {update.isError && <Alert variant="error">{(update.error as ApiError).message}</Alert>}

      <div className="oh-groupdetail">
        <VStack gap="4" className="oh-groupdetail__main">
          <Alert variant="info" size="small" inline>
            Første medlem som treffer datoen vinner. Flytt et medlem opp for å gi det høyere
            prioritet.
          </Alert>

          {tree.length === 0 ? (
            <BodyShort>
              Gruppen er tom. Legg til minst én regel, ellers får tjenestene som bruker gruppen ingen
              åpningstider.
            </BodyShort>
          ) : (
            <GroupTree
              nodes={tree}
              unreachable={unreachable}
              onMove={move}
              onRemove={(node) => {
                setMembers((prev) => prev.filter((id) => id !== node.id));
                setAnnouncement(`${node.name} er fjernet fra listen. Lagre for å bekrefte.`);
              }}
            />
          )}

          {/* Hver flytting bekreftes for skjermlesere uten å avbryte. */}
          <span aria-live="polite" className="oh-sr-only">
            {announcement}
          </span>

          <HStack gap="4" wrap>
            <Button variant="secondary" size="small" onClick={() => setAddKind('rule')}>
              Legg til regel
            </Button>
            <Button variant="secondary" size="small" onClick={() => setAddKind('group')}>
              Legg til undergruppe
            </Button>
          </HStack>

          <HStack gap="4" wrap align="center">
            <Button
              disabled={!dirty}
              loading={update.isPending}
              onClick={async () => {
                await update.mutateAsync({ id: groupId, name: group.data!.name, ruleGroupIds: members });
                setSaved(true);
              }}
            >
              Lagre rekkefølgen
            </Button>
            {dirty && (
              <Button
                variant="tertiary"
                onClick={() => setMembers(group.data!.ruleGroupIds ?? [])}
              >
                Forkast endringene
              </Button>
            )}
            <Button variant="danger" size="small" onClick={() => setDeleteOpen(true)}>
              Slett gruppen
            </Button>
          </HStack>
        </VStack>

        <aside className="oh-groupdetail__side">
          <GroupTestPanel groupId={groupId} nodes={tree} />
        </aside>
      </div>

      <AddMemberModal
        open={addKind !== null}
        kind={addKind ?? 'rule'}
        parentId={groupId}
        rules={rules.data ?? []}
        groups={(groups.data ?? []).filter((g) => g.id !== groupId)}
        registry={registry}
        existing={new Set(members)}
        onClose={() => setAddKind(null)}
        onAdd={(ids) => setMembers((prev) => [...prev, ...ids])}
      />

      <DeleteDialog
        open={deleteOpen}
        noun="Gruppen"
        name={group.data.name}
        conflicts={conflicts}
        note={`Gruppen er ikke i bruk. De ${members.length} medlemmene i gruppen blir ikke slettet — bare selve gruppen.`}
        resolveHref="/admin/tjenester"
        resolveLabel="Gå til tjenestene"
        deleting={remove.isPending}
        onClose={() => setDeleteOpen(false)}
        onConfirm={async () => {
          await remove.mutateAsync(groupId);
          navigate('/admin/grupper');
        }}
      />
    </VStack>
  );
}
