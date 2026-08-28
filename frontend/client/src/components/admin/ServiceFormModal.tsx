import { useState } from 'react';
import {
  Alert,
  BodyShort,
  Button,
  Modal,
  Select,
  TextField,
  VStack,
} from '@navikt/ds-react';
import type { OhGroup, Service, ServiceType } from '../../api/types';
import { ApiError } from '../../api/client';
import {
  useCreateService,
  useDeleteService,
  useSetServiceGroup,
  useUpdateService,
} from '../../hooks/admin';
import { DeleteDialog } from './DeleteDialog';
import './ServiceFormModal.css';
import { AppLink } from '../common/AppLink';

interface Props {
  /** `null` betyr ny tjeneste. */
  service: Service | null;
  groups: OhGroup[];
  currentGroupId: string | null;
  onClose: () => void;
}

/**
 * Tjenesteskjemaet — bevisst kort.
 *
 * Designet tok `type` ut av skjemaet, men backendens `ServiceRequest.type` er
 * påkrevd (avvik 11). Feltet er derfor beholdt ved oppretting, og eksisterende
 * verdi bevares ved redigering.
 *
 * Gruppekoblingen har et eget endepunkt, så en endring der lagres separat fra
 * resten av skjemaet.
 */
export function ServiceFormModal({ service, groups, currentGroupId, onClose }: Props) {
  const isNew = service === null;
  const create = useCreateService();
  const update = useUpdateService();
  const remove = useDeleteService();
  const setGroup = useSetServiceGroup();

  const [name, setName] = useState(service?.name ?? '');
  const [team, setTeam] = useState(service?.team ?? '');
  const [type, setType] = useState<ServiceType>(service?.type ?? 'TJENESTE');
  const [groupId, setGroupId] = useState(currentGroupId ?? '');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const error = (create.error ?? update.error ?? setGroup.error ?? remove.error) as ApiError | null;
  const saving = create.isPending || update.isPending || setGroup.isPending;
  const removingGroup = currentGroupId !== null && groupId === '';

  async function save() {
    setSubmitted(true);
    if (name.trim() === '' || team.trim() === '') return;

    const payload = {
      name: name.trim(),
      team: team.trim(),
      type,
      monitorlink: service?.monitorlink ?? null,
      logglink: service?.logglink ?? null,
      description: service?.description ?? null,
    };

    try {
      const saved = isNew
        ? await create.mutateAsync({ ...payload, ohGroupId: groupId || null })
        : await update.mutateAsync({ id: service.id, ...payload });

      // Ved oppretting tar `ohGroupId` i body seg av koblingen. Ved redigering
      // må den settes særskilt, siden PUT /service ikke rører koblingen.
      if (!isNew && groupId !== (currentGroupId ?? '')) {
        await setGroup.mutateAsync({ serviceId: saved.id, groupId: groupId || null });
      }
      onClose();
    } catch {
      // Vises via `error`.
    }
  }

  return (
    <>
      <Modal
        open
        onClose={onClose}
        width="small"
        header={{ heading: isNew ? 'Ny tjeneste' : `Rediger ${service.name}` }}
      >
        <Modal.Body>
          <VStack gap="5">
            {error && <Alert variant="error">{error.message}</Alert>}

            <TextField
              label="Navn"
              value={name}
              error={submitted && name.trim() === '' ? 'Tjenesten må ha et navn.' : undefined}
              onChange={(e) => setName(e.target.value)}
            />
            <TextField
              label="Team"
              description="Teamet som eier tjenesten."
              value={team}
              error={submitted && team.trim() === '' ? 'Tjenesten må ha et team.' : undefined}
              onChange={(e) => setTeam(e.target.value)}
            />
            <Select
              label="Type"
              value={type}
              onChange={(e) => setType(e.target.value as ServiceType)}
            >
              <option value="TJENESTE">Tjeneste</option>
              <option value="KOMPONENT">Komponent</option>
            </Select>

            <div className="oh-serviceform__group">
              <Select
                label="Åpningstidsgruppe"
                description="Gruppen bestemmer alt kalenderen viser for denne tjenesten. En tjeneste kan kobles til én gruppe."
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
              >
                <option value="">Ingen gruppe</option>
                {groups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.name}
                  </option>
                ))}
              </Select>
              {groupId && (
                <BodyShort size="small">
                  <AppLink to={`/admin/grupper/${groupId}`} onClick={onClose}>
                    Se gruppens regler og rekkefølge
                  </AppLink>
                </BodyShort>
              )}
            </div>

            {(removingGroup || (isNew && groupId === '')) && (
              <Alert variant="warning" size="small" inline>
                Uten gruppe kan vi ikke vise åpningstider for {name.trim() || 'tjenesten'}. Brukeren
                får en advarsel i kalenderen i stedet.
              </Alert>
            )}
          </VStack>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={() => void save()} loading={saving}>
            Lagre tjenesten
          </Button>
          <Button variant="tertiary" onClick={onClose}>
            Avbryt
          </Button>
          {!isNew && (
            <Button variant="danger" onClick={() => setDeleteOpen(true)}>
              Slett tjenesten
            </Button>
          )}
        </Modal.Footer>
      </Modal>

      {!isNew && (
        <DeleteDialog
          open={deleteOpen}
          noun="Tjenesten"
          name={service.name}
          // En tjeneste er aldri medlem av noe annet, så sletting er alltid trygg
          // for resten av oppsettet. Gruppen den peker på blir ikke slettet.
          conflicts={[]}
          note={`Tjenesten fjernes fra oversikten og kalenderen. Åpningstidsgruppen blir ikke slettet.`}
          deleting={remove.isPending}
          onClose={() => setDeleteOpen(false)}
          onConfirm={async () => {
            await remove.mutateAsync(service.id);
            setDeleteOpen(false);
            onClose();
          }}
        />
      )}
    </>
  );
}
