import { Component, input, output } from '@angular/core';
import { Dialog } from './dialog';

/**
 * Confirmation d'une action irréversible (suppression). L'erreur éventuelle du serveur s'affiche dans la
 * boîte, qui reste ouverte.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Dialog],
  template: `
    <app-dialog [open]="open()" [title]="title()" size="sm" [busy]="busy()" (closed)="cancel.emit()">
      <div class="flex gap-3">
        <div class="shrink-0 w-10 h-10 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
        </div>
        <div class="text-sm text-slate-700 space-y-2">
          <p>{{ message() }}</p>
          @if (error()) {
            <p class="bg-rose-50 border border-rose-200 text-rose-800 p-2.5 rounded-lg">{{ error() }}</p>
          }
        </div>
      </div>
      <div dialog-footer class="contents">
        <button type="button" (click)="cancel.emit()" [disabled]="busy()"
                class="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
          Annuler
        </button>
        <button type="button" (click)="confirm.emit()" [disabled]="busy()"
                class="rounded-lg bg-rose-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-50">
          {{ busy() ? 'Suppression…' : confirmLabel() }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class ConfirmDialog {
  readonly open = input(false);
  readonly title = input('Confirmer la suppression');
  readonly message = input('');
  readonly confirmLabel = input('Supprimer');
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly confirm = output<void>();
  readonly cancel = output<void>();
}
