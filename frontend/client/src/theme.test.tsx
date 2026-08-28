import { render, screen } from '@testing-library/react';
import { Button, Theme } from '@navikt/ds-react';
import { describe, expect, it } from 'vitest';

/**
 * Vaktpost for darkside-oppsettet i `main.tsx`.
 *
 * Appen importerer `@navikt/ds-css/darkside`, som kun inneholder klasser med
 * `aksel-`-prefiks. Komponentene skriver `navds-`-klasser helt til `<Theme>`
 * skrur på omdøpingen. Faller wrapperen bort, mister hele appen stilene sine
 * uten at noe krasjer eller feiler — den bare ser ustylet ut.
 */
describe('darkside-tema', () => {
  it('gir komponentene aksel-klasser når Theme er på plass', () => {
    render(
      <Theme theme="light">
        <Button>Lagre</Button>
      </Theme>,
    );
    expect(screen.getByRole('button').className).toContain('aksel-button');
  });

  it('faller tilbake til navds-klasser uten Theme, som darkside-CSS-en ikke kjenner', () => {
    render(<Button>Lagre</Button>);
    expect(screen.getByRole('button').className).toContain('navds-button');
  });
});
