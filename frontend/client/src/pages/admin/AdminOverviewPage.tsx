import { Link } from 'react-router-dom';
import { Alert, BodyShort, HGrid, Heading, Table, Tag, VStack } from '@navikt/ds-react';
import { AppLink } from '../../components/common/AppLink';
import { useGroups, useRules, useServiceGroupLinks } from '../../hooks/admin';
import { useServices, useSession } from '../../hooks/queries';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { ErrorState } from '../../components/common/ErrorState';
import { findIssues, summarize, unusedRules } from '../../lib/health';
import './AdminOverviewPage.css';

export function AdminOverviewPage() {
  const services = useServices();
  const groups = useGroups();
  const rules = useRules();
  const session = useSession();
  const links = useServiceGroupLinks(groups.data);

  if (services.isPending || groups.isPending || rules.isPending) return <DelayedLoader />;
  if (services.isError || groups.isError || rules.isError) {
    return <ErrorState message="Vi klarte ikke å hente oversikten." onRetry={() => location.reload()} />;
  }

  const issues = findIssues({
    services: services.data,
    groups: groups.data,
    rules: rules.data,
    linkedServiceIds: new Set(links.byService.keys()),
  });
  const summary = summarize(issues);
  const unused = unusedRules(groups.data, rules.data).length;

  return (
    <VStack gap="6">
      <div>
        <Heading level="1" size="large" spacing>
          Administrasjon
        </Heading>
        <BodyShort textColor="subtle">
          {session.data?.name ? `Innlogget som ${session.data.name}` : 'Innlogget'}
        </BodyShort>
      </div>

      {/* Avvikene først: «er noe galt?» før «hva vil du endre?». */}
      {summary ? (
        <Alert variant="warning">{summary}</Alert>
      ) : (
        !links.isPending && <Alert variant="success">Ingen avvik i oppsettet akkurat nå.</Alert>
      )}

      <HGrid gap="4" columns={{ xs: 1, sm: 3 }}>
        <KeyFigure
          to="/admin/tjenester"
          value={services.data.length}
          label={services.data.length === 1 ? 'tjeneste' : 'tjenester'}
        />
        <KeyFigure
          to="/admin/grupper"
          value={groups.data.length}
          label={groups.data.length === 1 ? 'åpningstidsgruppe' : 'åpningstidsgrupper'}
        />
        <KeyFigure
          to="/admin/regler"
          value={rules.data.length}
          label={rules.data.length === 1 ? 'regel' : 'regler'}
          note={unused > 0 ? `${unused} ikke i bruk` : undefined}
        />
      </HGrid>

      {issues.length > 0 && (
        <section>
          <Heading level="2" size="medium" spacing>
            Trenger oppmerksomhet
          </Heading>
          <Table size="small">
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell scope="col">Avvik</Table.HeaderCell>
                <Table.HeaderCell scope="col">Hva det betyr</Table.HeaderCell>
                <Table.HeaderCell scope="col">Handling</Table.HeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {issues.map((issue) => (
                <Table.Row key={issue.id}>
                  <Table.DataCell>
                    <Tag
                      size="small"
                      variant={issue.severity === 'warning' ? 'warning' : 'neutral'}
                    >
                      {issue.title}
                    </Tag>
                  </Table.DataCell>
                  <Table.DataCell>{issue.description}</Table.DataCell>
                  <Table.DataCell>
                    <AppLink to={issue.href}>{issue.actionLabel}</AppLink>
                  </Table.DataCell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table>
        </section>
      )}
    </VStack>
  );
}

function KeyFigure({
  to,
  value,
  label,
  note,
}: {
  to: string;
  value: number;
  label: string;
  note?: string;
}) {
  return (
    <Link to={to} className="oh-keyfigure">
      <span className="oh-keyfigure__value">{value}</span>
      <span className="oh-keyfigure__label">{label}</span>
      {note && <span className="oh-keyfigure__note">{note}</span>}
    </Link>
  );
}
