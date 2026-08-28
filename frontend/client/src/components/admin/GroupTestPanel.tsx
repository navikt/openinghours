import { useMemo, useState } from 'react';
import { Alert, BodyShort, Chips, Heading, VStack } from '@navikt/ds-react';
import { useGroupOnDate } from '../../hooks/admin';
import { addDays, formatDateLong, isoWeekday, todayIso } from '../../lib/date';
import { formatHours } from '../../lib/rule';
import { previewRule } from '../../lib/evaluate';
import type { TreeNode } from '../../lib/tree';
import { DelayedLoader } from '../common/DelayedLoader';
import './GroupTestPanel.css';

function nextWeekday(from: string, weekday: number): string {
  let date = from;
  for (let i = 0; i < 7; i += 1) {
    if (isoWeekday(date) === weekday) return date;
    date = addDays(date, 1);
  }
  return from;
}

function nextConstitutionDay(today: string): string {
  const year = Number(today.slice(0, 4));
  const thisYear = `${year}-05-17`;
  return today <= thisYear ? thisYear : `${year + 1}-05-17`;
}

/** Flater ut treet i evalueringsrekkefølge — samme rekkefølge som backend traverserer. */
function flatten(nodes: TreeNode[]): TreeNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)]);
}

/**
 * Test av hele gruppen mot en dato.
 *
 * **Resultatet er autoritativt** — det kommer fra backendens egen evaluator via
 * `GET /query/group/{id}`. Sporingslisten over hvilke medlemmer som ble vurdert
 * er derimot beregnet her, siden API-et bare svarer med regelen som vant. Den
 * gjør «hvorfor ble det dette?» synlig, og de to stemmer overens fordi de bruker
 * samme matchelogikk.
 */
export function GroupTestPanel({ groupId, nodes }: { groupId: string; nodes: TreeNode[] }) {
  const today = todayIso();
  const dates = useMemo(
    () => [
      { label: 'I dag', date: today },
      { label: 'Lørdag', date: nextWeekday(today, 6) },
      { label: '17. mai', date: nextConstitutionDay(today) },
    ],
    [today],
  );
  const [selected, setSelected] = useState(0);
  const active = dates[selected] ?? dates[0];
  const result = useGroupOnDate(groupId, active.date);

  const flat = useMemo(() => flatten(nodes).filter((n) => n.kind === 'rule' && !n.missing), [nodes]);
  const winnerIndex = flat.findIndex((node) => previewRule(node.rule ?? '', active.date).matches);

  return (
    <section className="oh-testpanel" aria-label="Test gruppen mot en dato">
      <Heading level="2" size="small" spacing>
        Test gruppen
      </Heading>

      <Chips>
        {dates.map((entry, index) => (
          <Chips.Toggle
            key={entry.label}
            selected={selected === index}
            onClick={() => setSelected(index)}
          >
            {entry.label}
          </Chips.Toggle>
        ))}
      </Chips>

      <BodyShort size="small" weight="semibold" className="oh-testpanel__date">
        {formatDateLong(active.date)}
      </BodyShort>

      {flat.length === 0 ? (
        <BodyShort size="small" textColor="subtle">
          Gruppen har ingen regler ennå.
        </BodyShort>
      ) : (
        <ol className="oh-testpanel__trace">
          {flat.map((node, index) => {
            const match = previewRule(node.rule ?? '', active.date);
            const won = index === winnerIndex;
            return (
              <li
                key={node.id}
                className={won ? 'oh-testpanel__step oh-testpanel__step--won' : 'oh-testpanel__step'}
              >
                <span className="oh-testpanel__prio">{node.priority}</span>
                <span>
                  <span className="oh-testpanel__name">{node.name}</span>
                  <span className="oh-testpanel__verdict">
                    {won
                      ? 'Treffer — denne vinner'
                      : match.matches
                        ? 'Treffer, men en regel over vant'
                        : 'Treffer ikke datoen'}
                  </span>
                </span>
              </li>
            );
          })}
        </ol>
      )}

      <div className="oh-testpanel__result" aria-live="polite">
        <Heading level="3" size="xsmall" spacing>
          Resultat
        </Heading>
        {result.isPending ? (
          <DelayedLoader />
        ) : result.isError ? (
          <Alert variant="info" size="small" inline>
            Backend fant ingen åpningstid for gruppen denne datoen. Da får tjenesten ingen visning.
          </Alert>
        ) : result.data ? (
          <VStack gap="1">
            <BodyShort size="small">
              {result.data.isOpen
                ? `Åpent ${formatHours(`${result.data.openingTime}-${result.data.closingTime}`)}`
                : 'Stengt'}
            </BodyShort>
            {result.data.matchedRule && (
              <BodyShort size="small" textColor="subtle">
                Regelen «{result.data.matchedRule.name}» vant.
              </BodyShort>
            )}
            {result.data.redDay && (
              <BodyShort size="small" textColor="subtle">
                Dagen er en rød dag i den norske helligdagskalenderen.
              </BodyShort>
            )}
          </VStack>
        ) : null}
      </div>
    </section>
  );
}
