import { Component, HostListener, input, output } from '@angular/core';
import { NgClass } from '@angular/common';

/**
 * Boîte de dialogue modale : fond assombri, en-tête, contenu défilant et pied (boutons).
 * Fermeture par la croix, la touche Échap ou un clic sur le fond, sauf pendant un enregistrement ({@link busy}).
 *
 * <pre>
 * <app-dialog [open]="open()" title="Nouvelle source" (closed)="open.set(false)">
 *   ...contenu...
 *   <div dialog-footer>...boutons...</div>
 * </app-dialog>
 * </pre>
 */
@Component({
  selector: 'app-dialog',
  imports: [NgClass],
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4" role="dialog"
           aria-modal="true" [attr.aria-label]="title()">
        <div class="absolute inset-0 bg-slate-950/60 backdrop-blur-xs" (click)="close()"></div>
        <div class="relative w-full bg-white sm:rounded-2xl rounded-t-2xl shadow-2xl flex flex-col max-h-[92vh]"
             [ngClass]="widthClass()">
          <div class="flex items-start justify-between gap-4 px-5 sm:px-6 py-4 border-b border-slate-200">
            <div>
              <h3 class="text-lg font-bold text-slate-900">{{ title() }}</h3>
              @if (subtitle()) {
                <p class="text-xs text-slate-500 mt-0.5">{{ subtitle() }}</p>
              }
            </div>
            <button type="button" (click)="close()" [disabled]="busy()" aria-label="Fermer"
                    class="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-40">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="flex-1 overflow-y-auto px-5 sm:px-6 py-5">
            <ng-content />
          </div>
          <div class="px-5 sm:px-6 py-3.5 border-t border-slate-200 bg-slate-50 sm:rounded-b-2xl flex flex-wrap items-center justify-end gap-2">
            <ng-content select="[dialog-footer]" />
          </div>
        </div>
      </div>
    }
  `,
})
export class Dialog {
  readonly open = input(false);
  readonly title = input('');
  readonly subtitle = input<string | null | undefined>(null);
  readonly size = input<'sm' | 'md' | 'lg' | 'xl'>('md');
  /** Enregistrement en cours : la fermeture est bloquée. */
  readonly busy = input(false);
  readonly closed = output<void>();

  protected widthClass(): string {
    return { sm: 'sm:max-w-md', md: 'sm:max-w-xl', lg: 'sm:max-w-3xl', xl: 'sm:max-w-5xl' }[this.size()];
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close();
    }
  }

  close(): void {
    if (!this.busy()) {
      this.closed.emit();
    }
  }
}
