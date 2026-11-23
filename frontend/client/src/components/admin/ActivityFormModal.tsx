import { useState, type FormEvent } from 'react';
import {
  Alert,
  BodyShort,
  Button,
  Checkbox,
  CheckboxGroup,
  Modal,
  Textarea,
  TextField,
  VStack,
} from '@navikt/ds-react';
import type { Activity, ActivityInput, Service } from '../../api/types';
import { ApiError } from '../../api/client';
import { activityDateTimeInput } from '../../lib/activity';
import { useCreateActivity, useUpdateActivity } from '../../hooks/activities';

interface Props {
  activity: Activity | null;
  services?: Service[];
  servicesPending: boolean;
  servicesError: boolean;
  onRetryServices: () => void;
  onClose: () => void;
  onSaved: () => void;
}

const FORM_ID = 'activity-form';

export function ActivityFormModal({
  activity,
  services,
  servicesPending,
  servicesError,
  onRetryServices,
  onClose,
  onSaved,
}: Props) {
  const create = useCreateActivity();
  const update = useUpdateActivity();
  const [header, setHeader] = useState(activity?.header ?? '');
  const [message, setMessage] = useState(activity?.message ?? '');
  const [serviceIds, setServiceIds] = useState<string[]>(activity?.serviceIds ?? []);
  const [start, setStart] = useState(activityDateTimeInput(activity?.start ?? null));
  const [end, setEnd] = useState(activityDateTimeInput(activity?.end ?? null));
  const [onlyShowForNavEmployees, setOnlyShowForNavEmployees] = useState(
    activity?.onlyShowForNavEmployees ?? false,
  );
  const [serviceSearch, setServiceSearch] = useState('');
  const saving = create.isPending || update.isPending;
  const mutationError = create.error ?? update.error;
  const servicesLoaded = services !== undefined;
  const visibleServices =
    services?.filter(
      (service) =>
        serviceIds.includes(service.id) ||
        service.name.toLocaleLowerCase('nb').includes(serviceSearch.trim().toLocaleLowerCase('nb')),
    ) ?? [];

  function close() {
    if (!saving) onClose();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!servicesLoaded || saving) return;

    const input: ActivityInput = {
      serviceIds,
      header,
      message,
      start: start || null,
      end: end || null,
      onlyShowForNavEmployees,
    };

    if (activity) {
      update.mutate({ id: activity.id, ...input }, { onSuccess: onSaved });
    } else {
      create.mutate(input, { onSuccess: onSaved });
    }
  }

  return (
    <Modal
      open
      onClose={close}
      onBeforeClose={() => !saving}
      width="small"
      header={{ heading: activity ? 'Rediger driftsmelding' : 'Ny driftsmelding' }}
    >
      <Modal.Body>
        <form id={FORM_ID} onSubmit={submit}>
          <VStack gap="4">
            {mutationError && (
              <Alert variant="error">
                {mutationError instanceof ApiError
                  ? mutationError.message
                  : 'Driftsmeldingen kunne ikke lagres. Prøv igjen.'}
              </Alert>
            )}

            <TextField
              label="Tittel"
              name="header"
              value={header}
              onChange={(event) => setHeader(event.target.value)}
              required
              maxLength={200}
            />
            <Textarea
              label="Melding"
              name="message"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              required
              minRows={3}
            />

            <div className="oh-activityform__services">
              <TextField
                label="Søk etter tjeneste"
                value={serviceSearch}
                onChange={(event) => setServiceSearch(event.target.value)}
              />
              {servicesPending && (
                <BodyShort role="status">Henter tjenester …</BodyShort>
              )}
              {servicesError && (
                <Alert variant="error">
                  Vi klarte ikke å hente tjenestene.
                  <Button type="button" variant="secondary" size="small" onClick={onRetryServices}>
                    Prøv igjen
                  </Button>
                </Alert>
              )}
              {servicesLoaded && (
                <CheckboxGroup
                  legend="Tjenester (valgfritt)"
                  value={serviceIds}
                  onChange={setServiceIds}
                >
                  {visibleServices.map((service) => (
                    <Checkbox key={service.id} value={service.id}>
                      {service.name}
                    </Checkbox>
                  ))}
                </CheckboxGroup>
              )}
              {servicesLoaded && services.length === 0 && (
                <BodyShort>Ingen tjenester tilgjengelig.</BodyShort>
              )}
              {servicesLoaded && services.length > 0 && visibleServices.length === 0 && (
                <BodyShort role="status">Ingen tjenester passer søket.</BodyShort>
              )}
              <BodyShort size="small">
                Ingen valgte tjenester betyr at meldingen gjelder generelt.
              </BodyShort>
            </div>

            <fieldset className="oh-activityform__times">
              <legend>Tidsrom (valgfritt)</legend>
              <BodyShort size="small" className="oh-activityform__timezone">
                Klokkeslett er i norsk tid.
              </BodyShort>
              <label>
                Fra
                <input
                  type="datetime-local"
                  name="start"
                  value={start}
                  onChange={(event) => setStart(event.target.value)}
                />
              </label>
              <label>
                Til
                <input
                  type="datetime-local"
                  name="end"
                  value={end}
                  onChange={(event) => setEnd(event.target.value)}
                />
              </label>
            </fieldset>

            <Checkbox
              checked={onlyShowForNavEmployees}
              onChange={(event) => setOnlyShowForNavEmployees(event.target.checked)}
            >
              Vis kun for Nav-ansatte
            </Checkbox>
          </VStack>
        </form>
      </Modal.Body>
      <Modal.Footer>
        <Button
          type="submit"
          form={FORM_ID}
          loading={saving}
          disabled={!servicesLoaded || saving}
        >
          Lagre driftsmeldingen
        </Button>
        <Button type="button" variant="tertiary" onClick={close} disabled={saving}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
