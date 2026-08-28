import { useEffect, useState } from 'react';
import { nowMinutes, todayIso } from '../lib/date';

/**
 * Klokken i Europe/Oslo, oppdatert hvert minutt.
 *
 * Intervallet er justert mot minuttgrensen slik at «Åpent nå» skifter når klokken
 * faktisk skifter, ikke opptil 59 sekunder senere.
 */
export function useNow() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;
    const msToNextMinute = 60_000 - (Date.now() % 60_000);
    const timeout = setTimeout(() => {
      setNow(new Date());
      interval = setInterval(() => setNow(new Date()), 60_000);
    }, msToNextMinute);

    return () => {
      clearTimeout(timeout);
      if (interval) clearInterval(interval);
    };
  }, []);

  return { now, today: todayIso(now), minutes: nowMinutes(now) };
}
