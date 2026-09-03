import { useEffect, useState } from 'react';

/**
 * Returnerer `value` forsinket med `delayMs`, og nullstiller ventetiden hver
 * gang verdien endrer seg.
 *
 * Brukes til å holde et inntastingsfelt responsivt mens den avledede verdien —
 * typisk et søk som speiles i URL-en — bare oppdateres når brukeren tar en pause.
 */
export function useDebounced<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
