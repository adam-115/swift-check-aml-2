import { Routes } from '@angular/router';
import { SourcesPage } from './pages/sources/sources';
import { ReferentielPage } from './pages/referentiel/referentiel';
import { CoveragePage } from './pages/coverage/coverage';

export const routes: Routes = [
  { path: 'sources', component: SourcesPage, title: 'Sources · Swift Check' },
  { path: 'referentiel', component: ReferentielPage, title: 'Référentiel PEP · Swift Check' },
  { path: 'coverage', component: CoveragePage, title: 'Couverture PEP · Swift Check' },
  { path: '', redirectTo: 'sources', pathMatch: 'full' },
  { path: '**', redirectTo: 'sources' },
];
