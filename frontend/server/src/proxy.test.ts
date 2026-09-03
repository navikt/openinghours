import { describe, expect, it } from 'vitest';
import { maskResponse } from './masking.ts';
import { isAllowed } from './proxy.ts';
import { isAdmin } from './auth.ts';

const SERVICE_ID = '11111111-2222-3333-4444-555555555555';

describe('isAllowed', () => {
  // Hele appen ligger bak ansatt.nav.no med autoLogin, så alt krever innlogging.
  // Sjekken er forsvar i dybden mot kall som omgår Wonderwall-sidecaren.
  // Slik appen kjører i dag: bak ansatt.nav.no, der alt krever innlogging.
  it('avviser alt fra uinnloggede når kalenderen ikke er offentlig', () => {
    expect(isAllowed('/openinghours/service', 'GET', false)).toBe(false);
    expect(isAllowed('/openinghours/daily', 'GET', false)).toBe(false);
    expect(isAllowed(`/openinghours/query/service/${SERVICE_ID}/range`, 'GET', false)).toBe(false);
  });

  it('slipper uinnloggede inn på kalenderen når den er offentlig', () => {
    expect(isAllowed('/openinghours/service', 'GET', false, true)).toBe(true);
    expect(isAllowed('/openinghours/daily', 'GET', false, true)).toBe(true);
    expect(isAllowed(`/openinghours/query/service/${SERVICE_ID}/range`, 'GET', false, true)).toBe(
      true,
    );
  });

  it('holder admin stengt for uinnloggede også når kalenderen er offentlig', () => {
    expect(isAllowed('/openinghours/rule', 'GET', false, true)).toBe(false);
    expect(isAllowed('/openinghours/group', 'GET', false, true)).toBe(false);
    expect(isAllowed('/openinghours/service', 'POST', false, true)).toBe(false);
  });

  it('slipper gjennom kalenderoppslag for innloggede', () => {
    expect(isAllowed('/openinghours/service', 'GET', true)).toBe(true);
    expect(isAllowed('/openinghours/daily', 'GET', true)).toBe(true);
    expect(isAllowed(`/openinghours/query/service/${SERVICE_ID}/range`, 'GET', true)).toBe(true);
    expect(isAllowed(`/openinghours/query/group/${SERVICE_ID}`, 'GET', true)).toBe(true);
  });

  it('slipper gjennom admin-oppslag for medlemmer av admingruppen', () => {
    expect(isAllowed('/openinghours/rule', 'GET', true, false, true)).toBe(true);
    expect(isAllowed('/openinghours/group', 'GET', true, false, true)).toBe(true);
    expect(isAllowed(`/openinghours/group/${SERVICE_ID}/associations`, 'GET', true, false, true)).toBe(
      true,
    );
  });

  it('stenger admin-oppslag for innloggede utenfor admingruppen', () => {
    expect(isAllowed('/openinghours/rule', 'GET', true)).toBe(false);
    expect(isAllowed('/openinghours/group', 'GET', true)).toBe(false);
    expect(isAllowed(`/openinghours/group/${SERVICE_ID}/associations`, 'GET', true)).toBe(false);
  });

  it('slipper gjennom mutasjoner kun for medlemmer av admingruppen', () => {
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
      expect(isAllowed('/openinghours/service', method, false)).toBe(false);
      // Innlogget er ikke lenger nok — det er kjernen i gruppestyringen.
      expect(isAllowed('/openinghours/service', method, true)).toBe(false);
      expect(isAllowed('/openinghours/service', method, true, false, true)).toBe(true);
      expect(isAllowed('/openinghours/rule', method, true, false, true)).toBe(true);
    }
  });

  it('lar ikke publicAccess åpne for mutasjoner', () => {
    // publicAccess styrer lesing av kalenderen, aldri skriving.
    for (const method of ['POST', 'PUT', 'DELETE']) {
      expect(isAllowed('/openinghours/rule', method, true, true)).toBe(false);
      expect(isAllowed('/openinghours/rule', method, false, true)).toBe(false);
    }
  });

  it('tillater kobling av tjeneste til gruppe', () => {
    expect(
      isAllowed(`/openinghours/service/${SERVICE_ID}/oh-group/${SERVICE_ID}`, 'PUT', true, false, true),
    ).toBe(true);
    expect(
      isAllowed(`/openinghours/service/${SERVICE_ID}/oh-group`, 'DELETE', true, false, true),
    ).toBe(true);
  });

  it('avviser ukjente stier — proxyen er ikke åpen', () => {
    expect(isAllowed('/actuator/env', 'GET', true, false, true)).toBe(false);
    expect(isAllowed('/../../etc/passwd', 'GET', true, false, true)).toBe(false);
    expect(isAllowed('/openinghours/swagger-ui', 'GET', true, false, true)).toBe(false);
  });

  it('avviser mutasjoner mot ruter som bare kan leses', () => {
    expect(isAllowed('/openinghours/daily', 'POST', true, false, true)).toBe(false);
    expect(
      isAllowed(`/openinghours/query/service/${SERVICE_ID}`, 'DELETE', true, false, true),
    ).toBe(false);
  });

  it('avviser UUID-ruter med ugyldig id', () => {
    expect(isAllowed('/openinghours/query/service/ikke-en-uuid/range', 'GET', true)).toBe(false);
  });
});

describe('maskResponse', () => {
  const internDag = {
    resourceId: SERVICE_ID,
    date: '2026-05-12',
    isOpen: true,
    openingTime: '08:00',
    closingTime: '15:30',
    onlyShowForNavEmployees: true,
    matchedRule: { name: 'Intern drift', rule: '??.??.???? ? 1,2,3,4,5 08:00-15:30' },
    displayHeader: 'Intern',
    displayText: 'Kun for ansatte',
  };

  it('lar alt stå urørt for innloggede', () => {
    expect(maskResponse(internDag, true)).toEqual(internDag);
  });

  it('fjerner tider og regelinfo for uinnloggede', () => {
    const result = maskResponse(internDag, false) as Record<string, unknown>;
    expect(result.masked).toBe(true);
    expect(result.openingTime).toBe('00:00');
    expect(result.closingTime).toBe('00:00');
    expect(result.isOpen).toBe(false);
    expect(result.matchedRule).toBeUndefined();
    expect(result.displayHeader).toBeNull();
    expect(result.displayText).toBeNull();
    expect(JSON.stringify(result)).not.toContain('Intern drift');
  });

  it('nullstiller ustabilitetsflagget, som hører til den skjulte regelen', () => {
    const result = maskResponse(
      { ...internDag, unstableOpeningHours: true },
      false,
    ) as Record<string, unknown>;
    expect(result.unstableOpeningHours).toBe(false);
  });

  it('beholder ustabilitetsflagget på offentlige dager', () => {
    const offentlig = { ...internDag, onlyShowForNavEmployees: false, unstableOpeningHours: true };
    expect((maskResponse(offentlig, false) as Record<string, unknown>).unstableOpeningHours).toBe(
      true,
    );
  });

  it('rører ikke offentlige dager', () => {
    const offentlig = { ...internDag, onlyShowForNavEmployees: false };
    expect(maskResponse(offentlig, false)).toEqual(offentlig);
  });

  it('masker enkeltdager inne i en liste', () => {
    const result = maskResponse(
      [{ ...internDag, onlyShowForNavEmployees: false }, internDag],
      false,
    ) as Array<Record<string, unknown>>;
    expect(result[0].masked).toBeUndefined();
    expect(result[1].masked).toBe(true);
  });

  it('masker dager som ligger nestet i et objekt', () => {
    const result = maskResponse({ [SERVICE_ID]: internDag }, false) as Record<
      string,
      Record<string, unknown>
    >;
    expect(result[SERVICE_ID].masked).toBe(true);
  });

  it('tåler null og primitiver', () => {
    expect(maskResponse(null, false)).toBeNull();
    expect(maskResponse('tekst', false)).toBe('tekst');
    expect(maskResponse(42, false)).toBe(42);
  });
});

describe('isAdmin', () => {
  const GROUP = '01a18f07-4dc7-4426-a407-09a1021dc024';
  const ANNEN = 'ffffffff-ffff-ffff-ffff-ffffffffffff';

  /** Bygger en request med et usignert token, slik Wonderwall ville satt det. */
  function req(claims: Record<string, unknown> | null) {
    const header = claims
      ? `Bearer x.${Buffer.from(JSON.stringify(claims), 'utf8').toString('base64url')}.y`
      : undefined;
    return {
      header: (name: string) =>
        name.toLowerCase() === 'authorization' ? header : undefined,
    } as unknown as Parameters<typeof isAdmin>[0];
  }

  it('gir tilgang til medlemmer av gruppen', () => {
    expect(isAdmin(req({ name: 'Kari', groups: [GROUP] }), GROUP)).toBe(true);
  });

  it('gir tilgang selv om brukeren er med i flere grupper', () => {
    expect(isAdmin(req({ name: 'Kari', groups: [ANNEN, GROUP] }), GROUP)).toBe(true);
  });

  it('nekter innloggede som ikke er medlem', () => {
    expect(isAdmin(req({ name: 'Ola', groups: [ANNEN] }), GROUP)).toBe(false);
  });

  it('nekter når groups-claimet mangler helt', () => {
    // Entra ID utelater claimet når brukeren ikke er med i noen av gruppene
    // appen ber om. Det skal tolkes som «ingen tilgang», ikke som en feil.
    expect(isAdmin(req({ name: 'Ola' }), GROUP)).toBe(false);
  });

  it('nekter uinnloggede', () => {
    expect(isAdmin(req(null), GROUP)).toBe(false);
  });

  it('lar alle innloggede være admin når ingen gruppe er satt', () => {
    // Fallback for lokal utvikling uten en ekte Entra ID-gruppe.
    expect(isAdmin(req({ name: 'Ola' }), '')).toBe(true);
    expect(isAdmin(req(null), '')).toBe(false);
  });

  it('tåler et groups-claim med feil type uten å kaste', () => {
    expect(isAdmin(req({ name: 'Ola', groups: 'ikke-en-liste' }), GROUP)).toBe(false);
    expect(isAdmin(req({ name: 'Ola', groups: [1, 2] }), GROUP)).toBe(false);
  });
});
