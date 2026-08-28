import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  BodyShort,
  Button,
  Checkbox,
  ConfirmationPanel,
  HStack,
  Heading,
  Modal,
  TextField,
  Textarea,
  VStack,
} from '@navikt/ds-react';
import { ApiError } from '../../api/client';
import {
  useDeleteRule,
  useRule,
  useRuleGroups,
  useRules,
  useUpdateRule,
  useUpsertRule,
} from '../../hooks/admin';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { DeleteDialog, type Conflict } from '../../components/admin/DeleteDialog';
import { RulePreview } from '../../components/admin/RulePreview';
import { RuleWizard } from '../../components/admin/RuleWizard';
import { DelayedLoader } from '../../components/common/DelayedLoader';
import { ErrorState } from '../../components/common/ErrorState';
import { EMPTY_FIELDS, buildRule, toFields, type RuleFields } from '../../lib/rulebuild';
import { validateRule } from '../../lib/validate';
import './RuleFormPage.css';

const DEFAULT_EXPR = buildRule(EMPTY_FIELDS);

export function RuleFormPage() {
  const { ruleId } = useParams();
  const isNew = ruleId === undefined;
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const existing = useRule(ruleId);
  const allRules = useRules();
  const usedIn = useRuleGroups(ruleId);
  const upsert = useUpsertRule();
  const update = useUpdateRule();
  const remove = useDeleteRule();

  const [name, setName] = useState('');
  const [expr, setExpr] = useState(DEFAULT_EXPR);
  const [fields, setFields] = useState<RuleFields | null>(EMPTY_FIELDS);
  const [header, setHeader] = useState('');
  const [text, setText] = useState('');
  const [onlyEmployees, setOnlyEmployees] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(searchParams.get('slett') === '1');
  const [saved, setSaved] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  // Fyll skjemaet når regelen er hentet. Kjører bare når id-en endrer seg, slik
  // at en bakgrunnsrefetch ikke overskriver det brukeren holder på å skrive.
  useEffect(() => {
    const rule = existing.data;
    if (!rule) return;
    setName(rule.name);
    setExpr(rule.rule);
    setFields(toFields(rule.rule));
    setHeader(rule.header ?? '');
    setText(rule.text ?? '');
    setOnlyEmployees(rule.onlyShowForNavEmployees);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existing.data?.id]);

  // Validering skjer med forsinkelse, slik at et halvskrevet klokkeslett ikke
  // rekker å bli en feilmelding.
  const debouncedExpr = useDebouncedValue(expr, 300);
  const error = useMemo(() => validateRule(debouncedExpr), [debouncedExpr]);

  /**
   * `PUT /rule` er en upsert på navn: et navn som allerede finnes overskriver
   * den regelen i stillhet. Vi advarer før det skjer (avvik 7).
   */
  const nameCollision =
    isNew && allRules.data?.some((r) => r.name.trim().toLowerCase() === name.trim().toLowerCase());

  const nameError = submitted && name.trim() === '' ? 'Regelen må ha et navn.' : undefined;
  const saving = upsert.isPending || update.isPending;
  const mutationError = (upsert.error ?? update.error ?? remove.error) as ApiError | null;

  const ruleConflicts: Conflict[] = (usedIn.data ?? []).map((group) => ({
    id: group.id,
    kind: 'Gruppe' as const,
    name: group.name,
    context: `${(group.ruleGroupIds ?? []).length} medlemmer`,
    href: `/admin/grupper/${group.id}`,
  }));

  function handleFieldsChange(next: RuleFields) {
    setFields(next);
    setExpr(buildRule(next));
  }

  function handleExprChange(next: string) {
    setExpr(next);
    setFields(toFields(next));
  }

  function handleReconnect() {
    const next = toFields(expr) ?? EMPTY_FIELDS;
    setFields(next);
    setExpr(buildRule(next));
  }

  function attemptSave() {
    setSubmitted(true);
    if (name.trim() === '' || validateRule(expr) !== null) return;
    // Er regelen i bruk, skal konsekvensen vises før endringen, ikke etter.
    if ((usedIn.data?.length ?? 0) > 0 || nameCollision) {
      setConfirmOpen(true);
      return;
    }
    void save();
  }

  async function save() {
    setConfirmOpen(false);
    const payload = {
      name: name.trim(),
      rule: expr.trim(),
      header: header.trim() || null,
      text: text.trim() || null,
      onlyShowForNavEmployees: onlyEmployees,
    };
    try {
      if (isNew) {
        const created = await upsert.mutateAsync(payload);
        navigate(`/admin/regler/${created.id}`, { replace: true });
      } else {
        await update.mutateAsync({ id: ruleId, ...payload });
      }
      setSaved(true);
    } catch {
      // Feilen vises via mutationError. Vi svelger den her for å unngå at en
      // avvist lagring blir en ubehandlet promise-avvisning.
    }
  }

  if (!isNew && existing.isPending) return <DelayedLoader />;
  if (!isNew && existing.isError) {
    return <ErrorState message="Vi fant ikke regelen. Den kan ha blitt slettet." />;
  }

  return (
    <VStack gap="5">
      <div>
        <BodyShort size="small">
          <Link to="/admin/regler">Tilbake til regler</Link>
        </BodyShort>
        <Heading level="1" size="large">
          {isNew ? 'Ny regel' : name || 'Rediger regel'}
        </Heading>
      </div>

      {saved && (
        <Alert variant="success" closeButton onClose={() => setSaved(false)}>
          Regelen er lagret.{' '}
          {(usedIn.data?.length ?? 0) > 0 && (
            <>
              Den brukes i{' '}
              {usedIn.data?.map((group, i) => (
                <span key={group.id}>
                  {i > 0 && ', '}
                  <Link to={`/admin/grupper/${group.id}`}>{group.name}</Link>
                </span>
              ))}
              .
            </>
          )}
        </Alert>
      )}

      {mutationError && <Alert variant="error">{mutationError.message}</Alert>}

      <div className="oh-ruleform">
        <VStack gap="5" className="oh-ruleform__main">
          <TextField
            label="Navn på regelen"
            description="Brukes i grupper og i oversikten. Beskriv hva regelen gjør, ikke når den ble laget."
            value={name}
            error={nameError}
            onChange={(e) => setName(e.target.value)}
          />

          {nameCollision && (
            <Alert variant="warning" size="small">
              Det finnes allerede en regel som heter «{name.trim()}». Lagrer du nå, blir den
              eksisterende regelen overskrevet — den blir ikke en ny regel.
            </Alert>
          )}

          <RuleWizard
            expr={expr}
            fields={fields}
            error={error}
            onFieldsChange={handleFieldsChange}
            onExprChange={handleExprChange}
            onReconnect={handleReconnect}
          />

          <TextField
            label="Overskrift til brukeren"
            description="Valgfri. Vises øverst i dagsdetaljene."
            value={header}
            onChange={(e) => setHeader(e.target.value)}
          />

          <Textarea
            label="Forklarende tekst"
            description="Valgfri. Skriv til brukeren i du-form, og si når tjenesten åpner igjen."
            value={text}
            minRows={3}
            onChange={(e) => setText(e.target.value)}
          />

          <div className="oh-ruleform__flags">
            <Checkbox
              checked={onlyEmployees}
              onChange={(e) => setOnlyEmployees(e.target.checked)}
              description="Dagen skjules for uinnloggede brukere."
            >
              Kun for Nav-ansatte
            </Checkbox>
            {/* Designet hadde også en «Rød dag»-avkrysning. Den finnes ikke:
                backend beregner røde dager fra helligdagskalenderen ved lesetid,
                og skrive-endepunktene setter aldri flagget (avvik 5). */}
            <BodyShort size="small" textColor="subtle">
              Røde dager settes ikke her. De beregnes automatisk fra den norske
              helligdagskalenderen og markeres i kalenderen uansett hvilken regel som traff.
            </BodyShort>
          </div>

          <HStack gap="4" wrap>
            <Button onClick={attemptSave} loading={saving}>
              Lagre regelen
            </Button>
            <Button variant="tertiary" as={Link} to="/admin/regler">
              Avbryt
            </Button>
            {!isNew && (
              <Button variant="danger" onClick={() => setDeleteOpen(true)}>
                Slett regelen
              </Button>
            )}
          </HStack>
        </VStack>

        <aside className="oh-ruleform__side">
          <VStack gap="4">
            <RulePreview expr={expr} invalid={Boolean(error)} />
            {(usedIn.data?.length ?? 0) > 0 && (
              <section className="oh-ruleform__usage">
                <Heading level="2" size="xsmall" spacing>
                  Brukt i {usedIn.data?.length} {usedIn.data?.length === 1 ? 'gruppe' : 'grupper'}
                </Heading>
                <VStack gap="1">
                  {usedIn.data?.map((group) => (
                    <Link key={group.id} to={`/admin/grupper/${group.id}`}>
                      {group.name}
                    </Link>
                  ))}
                </VStack>
                <BodyShort size="small" textColor="subtle">
                  En endring her slår ut i alle gruppene, og i alle tjenestene som bruker dem.
                </BodyShort>
              </section>
            )}
          </VStack>
        </aside>
      </div>

      <ConfirmSaveModal
        open={confirmOpen}
        groupCount={usedIn.data?.length ?? 0}
        overwriting={Boolean(nameCollision)}
        name={name.trim()}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void save()}
      />

      {!isNew && (
        <DeleteDialog
          open={deleteOpen}
          noun="Regelen"
          name={existing.data?.name ?? name}
          conflicts={ruleConflicts}
          resolveHref={usedIn.data?.[0] ? `/admin/grupper/${usedIn.data[0].id}` : undefined}
          deleting={remove.isPending}
          onClose={() => setDeleteOpen(false)}
          onConfirm={async () => {
            await remove.mutateAsync(ruleId);
            navigate('/admin/regler');
          }}
        />
      )}
    </VStack>
  );
}

function ConfirmSaveModal({
  open,
  groupCount,
  overwriting,
  name,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  groupCount: number;
  overwriting: boolean;
  name: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const [understood, setUnderstood] = useState(false);

  useEffect(() => {
    if (open) setUnderstood(false);
  }, [open]);

  return (
    <Modal open={open} onClose={onCancel} header={{ heading: 'Bekreft endringen' }} width="small">
      <Modal.Body>
        {overwriting ? (
          <ConfirmationPanel
            checked={understood}
            onChange={() => setUnderstood(!understood)}
            label={`Jeg vet at dette overskriver den eksisterende regelen «${name}».`}
          >
            En regel med dette navnet finnes allerede. Lagringen erstatter den, og den gamle
            versjonen kan ikke hentes tilbake.
          </ConfirmationPanel>
        ) : (
          <BodyShort>
            Regelen brukes i {groupCount} {groupCount === 1 ? 'gruppe' : 'grupper'}. Endringen slår
            ut alle stedene med én gang. Lagre endringen?
          </BodyShort>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button onClick={onConfirm} disabled={overwriting && !understood}>
          Lagre
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
