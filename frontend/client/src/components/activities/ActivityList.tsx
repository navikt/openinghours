import { BodyLong, BodyShort, Detail, Tag } from '@navikt/ds-react';
import type { TimedActivity } from '../../lib/activity';
import { affectedServiceNames, formatActivityPeriod } from '../../lib/activity';
import './ActivityList.css';

interface Props {
  activities: readonly TimedActivity[];
  servicesById: ReadonlyMap<string, { name: string }>;
  /** Skjul tjenestelisten når alt allerede gjelder én kjent tjeneste. */
  showServices?: boolean;
  /** Vis meldingsteksten. Kortere lister på forsiden klarer seg med overskriften. */
  showMessage?: boolean;
  loggedIn?: boolean;
}

/**
 * Vedlikehold og andre planlagte hendelser.
 *
 * Aktiviteter er ikke avvik i åpningstiden — tjenesten kan være åpen samtidig —
 * og vises derfor i en egen liste, ikke som chips i kalenderen.
 */
export function ActivityList({
  activities,
  servicesById,
  showServices = true,
  showMessage = true,
  loggedIn = false,
}: Props) {
  return (
    <ul className="oh-dayactivities">
      {activities.map((activity) => (
        <li key={activity.id} className="oh-dayactivity">
          <span className="oh-dayactivity__top">
            <BodyShort size="small" weight="semibold" as="span">
              {formatActivityPeriod(activity)}
            </BodyShort>
            {activity.ongoing && (
              <Tag variant="warning" size="xsmall">
                Pågår
              </Tag>
            )}
            {loggedIn && activity.onlyShowForNavEmployees && (
              <Tag variant="neutral" size="xsmall">
                Kun for Nav-ansatte
              </Tag>
            )}
          </span>
          <BodyShort weight="semibold">{activity.header}</BodyShort>
          {showServices && (
            <Detail textColor="subtle">
              {affectedServiceNames(activity, servicesById).join(', ')}
            </Detail>
          )}
          {showMessage && (
            <BodyLong size="small" className="oh-dayactivity__message">
              {activity.message}
            </BodyLong>
          )}
        </li>
      ))}
    </ul>
  );
}
