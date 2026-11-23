import { useState } from 'react';
import {
  Alert,
  BodyLong,
  BodyShort,
  Button,
  HStack,
  Heading,
  Tag,
  VStack,
} from '@navikt/ds-react';
import { useSearchParams } from 'react-router-dom';
import type { Activity } from '../../api/types';
import { ApiError } from '../../api/client';
import { DeleteDialog } from '../../components/admin/DeleteDialog';
import { ActivityFormModal } from '../../components/admin/ActivityFormModal';
import { EmptyState, ErrorState } from '../../components/common/ErrorState';
import { useActivitiesRange, useDeleteActivity } from '../../hooks/activities';
import { useServices, useSession } from '../../hooks/queries';
import { formatActivityDateTime } from '../../lib/activity';
import { addDays, todayIso } from '../../lib/date';
import './ActivitiesPage.css';

export function ActivitiesPage() {
  const session = useSession();

  if (session.isPending) {
    return (
      <p className="oh-activities__status" role="status">
        Henter tilgang …
      </p>
    );
  }
  if (session.isError) {
    return <ErrorState message="Vi klarte ikke å kontrollere tilgangen." onRetry={() => session.refetch()} />;
  }
  if (!session.data?.isAdmin) {
    return <Alert variant="warning">Du har ikke tilgang til driftsmeldinger.</Alert>;
  }

  return <ActivitiesContent />;
}

function ActivitiesContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState<Activity | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Activity | null>(null);
  const [success, setSuccess] = useState('');
  const today = todayIso();
  const from = searchParams.get('fra') ?? addDays(today, -30);
  const to = searchParams.get('til') ?? addDays(today, 365);
  const activities = useActivitiesRange(from, to);
  const services = useServices();
  const remove = useDeleteActivity();
  const serviceNames = new Map((services.data ?? []).map((service) => [service.id, service.name]));

  function updateDate(key: 'fra' | 'til', value: string) {
    const next = new URLSearchParams(searchParams);
    next.set(key, value);
    setSearchParams(next, { replace: true });
  }

  function startEditing(activity: Activity | 'new') {
    setSuccess('');
    setEditing(activity);
  }

  function startDeleting(activity: Activity) {
    setSuccess('');
    remove.reset();
    setDeleting(activity);
  }

  function finishEditing() {
    setEditing(null);
    setSuccess(editing === 'new' ? 'Driftsmeldingen er opprettet.' : 'Driftsmeldingen er lagret.');
  }

  function confirmDelete() {
    if (!deleting) return;
    remove.mutate(deleting.id, {
      onSuccess: () => {
        setDeleting(null);
        setSuccess('Driftsmeldingen er slettet.');
      },
    });
  }

  const deleteError = remove.error
    ? remove.error instanceof ApiError
      ? remove.error.message
      : 'Driftsmeldingen kunne ikke slettes. Prøv igjen.'
    : '';

  return (
    <VStack className="oh-activities" gap="5">
      <HStack align="end" justify="space-between" wrap gap="4">
        <Heading level="1" size="large">
          Driftsmeldinger
        </Heading>
        <Button onClick={() => startEditing('new')}>Ny driftsmelding</Button>
      </HStack>

      {success && (
        <Alert variant="success" closeButton onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      {deleteError && !deleting && (
        <Alert variant="error" aria-live="assertive">
          {deleteError}
        </Alert>
      )}

      <section className="oh-activities__filters" aria-label="Filtrer driftsmeldinger">
        <label>
          Fra
          <input type="date" value={from} onChange={(event) => updateDate('fra', event.target.value)} />
        </label>
        <label>
          Til
          <input type="date" value={to} onChange={(event) => updateDate('til', event.target.value)} />
        </label>
      </section>

      {services.isPending && (
        <BodyShort className="oh-activities__status" role="status">
          Henter tjenestelisten …
        </BodyShort>
      )}
      {services.isError && (
        <ErrorState message="Vi klarte ikke å hente tjenestene." onRetry={() => services.refetch()} />
      )}

      <section aria-label="Driftsmeldinger i valgt periode" aria-busy={activities.isPending}>
        {activities.isPending && (
          <BodyShort className="oh-activities__status" role="status">
            Henter driftsmeldinger …
          </BodyShort>
        )}
        {activities.isError && (
          <ErrorState
            message="Vi klarte ikke å hente driftsmeldingene."
            onRetry={() => activities.refetch()}
          />
        )}
        {activities.isSuccess && activities.data.length === 0 && (
          <EmptyState
            title="Ingen driftsmeldinger i perioden"
            description="Prøv en annen periode, eller opprett en ny driftsmelding."
            actionLabel="Ny driftsmelding"
            onAction={() => startEditing('new')}
          />
        )}
        {activities.isSuccess && activities.data.length > 0 && (
          <ul className="oh-activities__list">
            {activities.data.map((activity) => (
              <li key={activity.id}>
                <article className="oh-activity-card">
                  <div className="oh-activity-card__heading">
                    <Heading level="2" size="small">
                      {activity.header}
                    </Heading>
                    {activity.ongoing && <Tag size="small" variant="success">Pågår</Tag>}
                  </div>
                  <BodyLong>{activity.message}</BodyLong>
                  <dl className="oh-activity-card__details">
                    <div>
                      <dt>Tjenester</dt>
                      <dd>{describeServices(activity, serviceNames, services.isPending, services.isError)}</dd>
                    </div>
                    {(activity.start || activity.end) && (
                      <div>
                        <dt>Periode</dt>
                        <dd>
                          {activity.start && <span>Fra {formatActivityDateTime(activity.start)}</span>}
                          {activity.end && <span>Til {formatActivityDateTime(activity.end)}</span>}
                        </dd>
                      </div>
                    )}
                    {activity.onlyShowForNavEmployees && (
                      <div>
                        <dt>Synlighet</dt>
                        <dd>Kun for Nav-ansatte</dd>
                      </div>
                    )}
                  </dl>
                  <HStack className="oh-activity-card__actions" gap="2">
                    <Button
                      size="small"
                      variant="secondary"
                      aria-label={`Rediger driftsmeldingen «${activity.header}»`}
                      onClick={() => startEditing(activity)}
                    >
                      Rediger
                    </Button>
                    <Button
                      size="small"
                      variant="tertiary"
                      aria-label={`Slett driftsmeldingen «${activity.header}»`}
                      onClick={() => startDeleting(activity)}
                    >
                      Slett
                    </Button>
                  </HStack>
                </article>
              </li>
            ))}
          </ul>
        )}
      </section>

      {editing !== null && (
        <ActivityFormModal
          key={editing === 'new' ? 'new' : editing.id}
          activity={editing === 'new' ? null : editing}
          services={services.data}
          servicesPending={services.isPending}
          servicesError={services.isError}
          onRetryServices={() => services.refetch()}
          onClose={() => setEditing(null)}
          onSaved={finishEditing}
        />
      )}

      {deleting && (
        <DeleteDialog
          open
          noun="Driftsmeldingen"
          name={deleting.header}
          conflicts={[]}
          note="Driftsmeldingen fjernes fra forsiden. Åpningstidene endres ikke."
          error={deleteError || undefined}
          deleting={remove.isPending}
          onClose={() => {
            if (!remove.isPending) {
              setDeleting(null);
              remove.reset();
            }
          }}
          onConfirm={confirmDelete}
        />
      )}
    </VStack>
  );
}

function describeServices(
  activity: Activity,
  names: Map<string, string>,
  pending: boolean,
  failed: boolean,
): string {
  if (activity.serviceIds.length === 0) return 'Generell driftsmelding';
  if (pending) return 'Henter tjenestene …';
  if (failed) return 'Tjenestelisten er utilgjengelig';
  return activity.serviceIds.map((id) => names.get(id) ?? id).join(', ');
}
