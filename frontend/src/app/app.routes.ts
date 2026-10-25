import { Routes } from '@angular/router';
import { SourcesPage } from './pages/sources/sources';
import { ExecutionsPage } from './pages/executions/executions';
import { ExecutionDetailPage } from './pages/execution-detail/execution-detail';
import { CoveragePage } from './pages/coverage/coverage';

export const routes: Routes = [
  { path: 'sources', component: SourcesPage, title: 'Sources PEP · Swift Check' },
  { path: 'executions', component: ExecutionsPage, title: 'Exécutions · Swift Check' },
  { path: 'executions/:id', component: ExecutionDetailPage, title: 'Exécution · Swift Check' },
  { path: 'coverage', component: CoveragePage, title: 'Couverture PEP · Swift Check' },
  { path: '', redirectTo: 'sources', pathMatch: 'full' },
  { path: '**', redirectTo: 'sources' },
];
