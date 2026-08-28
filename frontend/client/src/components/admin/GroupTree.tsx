import { BodyShort, Button, HStack, Tag } from '@navikt/ds-react';
import { ArrowDownIcon, ArrowUpIcon } from '@navikt/aksel-icons';
import type { TreeNode } from '../../lib/tree';
import { formatRule } from '../../lib/rule';
import './GroupTree.css';
import { AppLink } from '../common/AppLink';

interface Props {
  nodes: TreeNode[];
  unreachable: Set<string>;
  /** Kun toppnivået kan omorganiseres her — undergrupper har sin egen side. */
  onMove?: (index: number, direction: -1 | 1) => void;
  onRemove?: (node: TreeNode) => void;
}

/**
 * Treet er en ordnet liste, ikke `role="tree"`: rekkefølgen *er* meningen, og
 * `<ol>` formidler «2 av 7» til skjermlesere uten at vi må bygge det selv.
 *
 * Dra-og-slipp er bevisst utelatt. Designet krever at det aldri er den eneste
 * måten å flytte på, og Opp/Ned-knappene dekker behovet fullt ut — også fra
 * tastatur, uten en egen tastaturmodell å lære.
 */
export function GroupTree({ nodes, unreachable, onMove, onRemove }: Props) {
  return (
    <ol className="oh-tree">
      {nodes.map((node, index) => (
        <li key={`${node.kind}-${node.id}`} className={`oh-tree__item oh-tree__item--${node.kind}`}>
          <div className="oh-tree__row">
            <span className="oh-tree__priority" aria-hidden>
              {node.priority}
            </span>

            <div className="oh-tree__body">
              <HStack gap="2" align="center" wrap>
                <Tag size="small" variant={node.kind === 'group' ? 'alt1' : 'neutral'}>
                  {node.kind === 'group' ? 'Gruppe' : 'Regel'}
                </Tag>
                {node.missing ? (
                  <span className="oh-tree__name">Ukjent medlem ({node.id})</span>
                ) : node.kind === 'group' ? (
                  <AppLink className="oh-tree__name" to={`/admin/grupper/${node.id}`}>
                    {node.name}
                  </AppLink>
                ) : (
                  <AppLink className="oh-tree__name" to={`/admin/regler/${node.id}`}>
                    {node.name}
                  </AppLink>
                )}
              </HStack>

              <BodyShort size="small" textColor="subtle">
                {node.missing
                  ? 'Id-en finnes i gruppen, men peker verken på en regel eller en gruppe. Fjern den.'
                  : node.kind === 'group'
                    ? `${node.memberCount} ${node.memberCount === 1 ? 'medlem' : 'medlemmer'}${
                        node.truncated ? ' · åpne gruppen for å se innholdet' : ''
                      }`
                    : formatRule(node.rule ?? '')}
              </BodyShort>

              {unreachable.has(node.id) && (
                <BodyShort size="small" className="oh-tree__warning">
                  Treffer aldri — en regel over dekker alle datoer. Det kan være tilsiktet.
                </BodyShort>
              )}
            </div>

            {onMove && onRemove && (
              <HStack gap="1" className="oh-tree__actions">
                <Button
                  size="small"
                  variant="tertiary-neutral"
                  icon={<ArrowUpIcon aria-hidden />}
                  disabled={index === 0}
                  onClick={() => onMove(index, -1)}
                  title={`Flytt ${node.name} opp`}
                >
                  Opp
                </Button>
                <Button
                  size="small"
                  variant="tertiary-neutral"
                  icon={<ArrowDownIcon aria-hidden />}
                  disabled={index === nodes.length - 1}
                  onClick={() => onMove(index, 1)}
                  title={`Flytt ${node.name} ned`}
                >
                  Ned
                </Button>
                <Button size="small" variant="tertiary" onClick={() => onRemove(node)}>
                  Fjern
                </Button>
              </HStack>
            )}
          </div>

          {node.children.length > 0 && (
            <GroupTree nodes={node.children} unreachable={unreachable} />
          )}
        </li>
      ))}
    </ol>
  );
}
