import { useEffect, useState } from 'react';
import { Loader } from '@navikt/ds-react';
import './DelayedLoader.css';

/**
 * Loader vises først etter 600 ms.
 *
 * Under den terskelen rekker de fleste kall å bli ferdige, og en loader som blinker
 * i 200 ms oppleves som støy — ikke som fremdrift.
 */
export function DelayedLoader({ delayMs = 600 }: { delayMs?: number }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), delayMs);
    return () => clearTimeout(timer);
  }, [delayMs]);

  if (!visible) return null;
  return (
    <div className="oh-delayed-loader">
      <Loader size="large" title="Henter åpningstider" />
    </div>
  );
}
