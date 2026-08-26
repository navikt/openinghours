import { useEffect, useState } from 'react';

/**
 * Forsinket verdi. Brukes til validering mens du skriver: 300 ms er lenge nok
 * til at «08:0» ikke rekker å bli en feilmelding, og kort nok til at kvitteringen
 * føles umiddelbar når du er ferdig.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
