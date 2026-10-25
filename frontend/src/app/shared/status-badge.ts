import { Component, computed, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { ExecutionStatus } from '../models/api';

/** Libellé français et couleurs de chaque statut d'exécution. */
const STATUS: Record<ExecutionStatus, { label: string; css: string; title: string }> = {
  SUCCESS: { label: 'Réussie', css: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', title: 'Fichier FTM généré' },
  FAILED: { label: 'Échec', css: 'bg-rose-50 text-rose-700 ring-rose-600/20', title: 'Erreur : rien n\'a été généré' },
  RUNNING: { label: 'En cours', css: 'bg-blue-50 text-blue-700 ring-blue-600/20', title: 'Exécution en cours' },
};

/** Pastille de statut d'exécution, utilisée dans les listes et le détail. */
@Component({
  selector: 'app-status-badge',
  imports: [NgClass],
  template: `
    @if (info(); as i) {
      <span [title]="i.title"
            class="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-semibold ring-1 ring-inset whitespace-nowrap"
            [ngClass]="i.css">
        <span class="w-1.5 h-1.5 rounded-full bg-current" [class.animate-pulse]="status() === 'RUNNING'"></span>{{ i.label }}
      </span>
    } @else {
      <span class="text-xs text-slate-400 italic">Jamais exécutée</span>
    }
  `,
})
export class StatusBadge {
  readonly status = input<ExecutionStatus | null | undefined>();
  protected readonly info = computed(() => {
    const s = this.status();
    return s ? STATUS[s] : null;
  });
}
