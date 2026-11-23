import { Alert, BodyLong, BodyShort, Box, Button, Heading, Skeleton, Tag } from '@navikt/ds-react';
import type { Activity, Service } from '../../api/types';
import { AppLink } from '../common/AppLink';
import { useActivitiesRange } from '../../hooks/activities';
import { useSession } from '../../hooks/queries';
import { formatActivityDateTime } from '../../lib/activity';
import { addDays } from '../../lib/date';
import './ActivityFeed.css';

export function ActivityFeed({ today, services }: { today: string; services: Service[] }) {
  const session = useSession();
  const loggedIn = session.data?.loggedIn === true;
  const activities = useActivitiesRange(today, addDays(today, 30), loggedIn);

  if (!loggedIn) return null;

  return (
    <Box.New
      as="section"
      className="oh-activity-feed"
      padding="6"
      borderRadius="large"
      borderWidth="1"
      borderColor="neutral-subtle"
      background="default"
      aria-labelledby="oh-activity-feed-heading"
    >
      <div className="oh-activity-feed__header">
        <Heading level="2" size="medium" id="oh-activity-feed-heading">
          Driftsmeldinger
        </Heading>
        {session.data?.isAdmin && (
          <AppLink to="/admin/aktiviteter">Administrer driftsmeldinger</AppLink>
        )}
      </div>

      {activities.isPending ? (
        <div className="oh-activity-feed__loading" role="status">
          <BodyShort>Henter driftsmeldinger</BodyShort>
          <Skeleton variant="text" width="100%" height="2rem" aria-hidden />
        </div>
      ) : activities.isError ? (
        <Alert variant="error" size="small" role="alert">
          <div className="oh-activity-feed__error">
            <BodyShort>Vi klarte ikke å hente driftsmeldingene.</BodyShort>
            <Button variant="secondary" size="small" onClick={() => activities.refetch()}>
              Prøv igjen
            </Button>
          </div>
        </Alert>
      ) : activities.data?.length ? (
        <ul className="oh-activity-feed__list">
          {activities.data.map((activity) => (
            <li key={activity.id}>
              <ActivityItem activity={activity} services={services} />
            </li>
          ))}
        </ul>
      ) : (
        <BodyShort>Ingen driftsmeldinger i denne perioden.</BodyShort>
      )}
    </Box.New>
  );
}

function ActivityItem({ activity, services }: { activity: Activity; services: Service[] }) {
  const headingId = `oh-activity-${activity.id}`;
  const serviceById = new Map(services.map((service) => [service.id, service]));
  const hasTime = Boolean(activity.start || activity.end);

  return (
    <article className="oh-activity-feed__item" aria-labelledby={headingId}>
      <div className="oh-activity-feed__title">
        <Heading level="3" size="small" id={headingId}>
          {activity.header}
        </Heading>
        {activity.ongoing && (
          <Tag variant="success-filled" size="small">
            Pågår
          </Tag>
        )}
        {activity.onlyShowForNavEmployees && (
          <Tag variant="neutral" size="small">
            Kun for Nav-ansatte
          </Tag>
        )}
      </div>

      <BodyLong className="oh-activity-feed__message">{activity.message}</BodyLong>

      {hasTime && (
        <div className="oh-activity-feed__time">
          {activity.start && activity.end ? (
            <>
              <time dateTime={activity.start}>{formatActivityDateTime(activity.start)}</time>
              <span aria-hidden="true">–</span>
              <time dateTime={activity.end}>{formatActivityDateTime(activity.end)}</time>
            </>
          ) : activity.start ? (
            <>
              <span>Fra</span>{' '}
              <time dateTime={activity.start}>{formatActivityDateTime(activity.start)}</time>
            </>
          ) : activity.end ? (
            <>
              <span>Til</span>{' '}
              <time dateTime={activity.end}>{formatActivityDateTime(activity.end)}</time>
            </>
          ) : null}
        </div>
      )}

      {activity.serviceIds.length > 0 && (
        <ul className="oh-activity-feed__services" aria-label="Berørte tjenester">
          {activity.serviceIds.map((serviceId, index) => {
            const service = serviceById.get(serviceId);
            return (
              <li key={`${serviceId}-${index}`}>
                <AppLink to={`/t/${serviceId}`}>{service?.name ?? serviceId}</AppLink>
              </li>
            );
          })}
        </ul>
      )}
    </article>
  );
}
