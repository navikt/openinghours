import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Theme } from '@navikt/ds-react';
/*
 * Darkside-bundelen, ikke '@navikt/ds-css'. Designet er spesifisert i --ax-*-tokens,
 * og de finnes kun her: legacy-bundelen definerer ingen av dem. Uten dette faller
 * 80 tokenoppslag — hvorav 46 er spacing — tilbake til ingenting, og nettleseren
 * dropper hele deklarasjonen. Bundelen tar også med Source Sans 3.
 *
 * Darkside døper om komponentklassene fra navds-* til aksel-*. Omdøpingen skrus på
 * av <Theme> under, så CSS-importen og wrapperen hører uløselig sammen.
 */
import '@navikt/ds-css/darkside';
import { App } from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* hasBackground={false}: body har allerede --ax-bg-sunken, og Theme ville
            ellers lagt en flate i bg-default oppå den. */}
        <Theme theme="light" hasBackground={false}>
          <App />
        </Theme>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
