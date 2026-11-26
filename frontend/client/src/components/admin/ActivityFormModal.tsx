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
  const [otherServices, setOtherServices] = useState((activity?.serviceNames ?? []).join(', '));
  const [serviceSearch, setServiceSearch] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const saving = create.isPending || update.isPending;
  const mutationError = create.error ?? update.error;
  const servicesLoaded = services !== undefined;
  const serviceNames = parseServiceNames(otherServices);
  const missingService = serviceIds.length === 0 && serviceNames.length === 0;
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
    setSubmitted(true);
    if (missingService || !start || !end) return;

    const input: ActivityInput = {
      serviceIds,
      serviceNames,
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
      header={{ heading: activity ? 'Rediger aktivitet' : 'Ny aktivitet' }}
    >
      <Modal.Body>
        <form id={FORM_ID} onSubmit={submit}>
          <VStack gap="4">
            {activity?.source === 'SLACK' && (
              <Alert variant="info" size="small">
                Aktiviteten er hentet fra #varsling-nedetid. Lagrer du endringer her, blir den
                manuell, og senere meldinger i Slack-tråden endrer den ikke lenger.
              </Alert>
            )}
            {mutationError && (
              <Alert variant="error">
                {mutationError instanceof ApiError
                  ? mutationError.message
                  : 'Aktiviteten kunne ikke lagres. Prøv igjen.'}
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
                  legend="Tjenester"
                  value={serviceIds}
                  onChange={setServiceIds}
                  error={
                    submitted && missingService
                      ? 'Velg minst én tjeneste, eller skriv inn en tjeneste utenfor katalogen.'
                      : undefined
                  }
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
              <TextField
                label="Andre tjenester"
                description="Tjenester som ikke finnes i katalogen, skilt med komma."
                value={otherServices}
                onChange={(event) => setOtherServices(event.target.value)}
              />
            </div>

            <fieldset className="oh-activityform__times">
              <legend>Tidsrom</legend>
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
                  required
                />
              </label>
              <label>
                Til
                <input
                  type="datetime-local"
                  name="end"
                  value={end}
                  onChange={(event) => setEnd(event.target.value)}
                  min={start || undefined}
                  required
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
          Lagre aktiviteten
        </Button>
        <Button type="button" variant="tertiary" onClick={close} disabled={saving}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

function parseServiceNames(value: string): string[] {
  const seen = new Set<string>();
  return value
    .split(',')
    .map((name) => name.trim())
    .filter((name) => {
      const key = name.toLocaleLowerCase('nb');
      if (!name || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}
