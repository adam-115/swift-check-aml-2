import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FunctionRequest, PepCategory, PepFunction } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { FormErrors, NO_ERRORS, formErrors } from '../../services/form-errors';
import { Dialog } from '../../shared/dialog';

/** Dialog de création ou de modification d'une fonction PEP ({@link fn} vide : création). */
@Component({
  selector: 'app-function-dialog',
  imports: [FormsModule, Dialog],
  template: `
    <app-dialog [open]="open()" [busy]="busy()" (closed)="closed.emit()"
                [title]="isEdit() ? 'Modifier la fonction' : 'Nouvelle fonction PEP'" [subtitle]="fn()?.label">
      @if (errors().message) {
        <div class="mb-4 bg-rose-50 border border-rose-200 text-rose-800 p-3 rounded-xl text-sm">{{ errors().message }}</div>
      }
      <form id="function-form" (ngSubmit)="save()" class="grid grid-cols-1 gap-4 text-sm">
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="categoryId">Catégorie *</label>
          <select id="categoryId" name="categoryId" [(ngModel)]="form.categoryId"
                  class="w-full rounded-lg border bg-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  [class.border-rose-400]="err('categoryId')" [class.border-slate-300]="!err('categoryId')">
            <option [ngValue]="null" disabled>Choisir une catégorie</option>
            @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.code }} · {{ c.label }}</option> }
          </select>
          @if (err('categoryId'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="label">Fonction *</label>
          <input id="label" name="label" [(ngModel)]="form.label" placeholder="Député"
                 class="w-full rounded-lg border px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                 [class.border-rose-400]="err('label')" [class.border-slate-300]="!err('label')">
          @if (err('label'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div class="sm:col-span-2">
            <label class="block text-xs font-semibold text-slate-600 mb-1" for="institution">Institution</label>
            <input id="institution" name="institution" [(ngModel)]="form.institution" placeholder="Chambre des Députés"
                   class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            @if (err('institution'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1" for="expectedHolders">Titulaires attendus</label>
            <input id="expectedHolders" name="expectedHolders" type="number" min="0" [(ngModel)]="form.expectedHolders"
                   class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            @if (err('expectedHolders'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
          </div>
        </div>
      </form>
      <div dialog-footer class="contents">
        <button type="button" (click)="closed.emit()" [disabled]="busy()"
                class="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Annuler</button>
        <button type="submit" form="function-form" [disabled]="busy()"
                class="rounded-lg bg-emerald-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50">
          {{ busy() ? 'Enregistrement…' : (isEdit() ? 'Enregistrer' : 'Créer la fonction') }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class FunctionDialog {
  private readonly api = inject(ApiService);

  readonly open = input(false);
  readonly fn = input<PepFunction | null>(null);
  readonly categories = input<PepCategory[]>([]);
  /** Catégorie proposée pour une nouvelle fonction. */
  readonly defaultCategoryId = input<number | null>(null);
  readonly saved = output<PepFunction>();
  readonly closed = output<void>();

  protected readonly busy = signal(false);
  protected readonly errors = signal<FormErrors>(NO_ERRORS);
  protected readonly isEdit = computed(() => !!this.fn());
  protected form: FunctionRequest = { categoryId: null, label: '', institution: '', expectedHolders: null };

  constructor() {
    effect(() => {
      if (this.open()) {
        const f = this.fn();
        this.form = f
          ? { categoryId: f.categoryId, label: f.label, institution: f.institution ?? '', expectedHolders: f.expectedHolders ?? null }
          : { categoryId: this.defaultCategoryId(), label: '', institution: '', expectedHolders: null };
        this.errors.set(NO_ERRORS);
      }
    });
  }

  err(name: string): string | null {
    return this.errors().fields[name] ?? null;
  }

  save(): void {
    this.busy.set(true);
    this.errors.set(NO_ERRORS);
    const f = this.fn();
    (f ? this.api.updateFunction(f.id, this.form) : this.api.createFunction(this.form)).subscribe({
      next: saved => {
        this.busy.set(false);
        this.saved.emit(saved);
      },
      error: err => {
        this.busy.set(false);
        this.errors.set(formErrors(err));
      },
    });
  }
}
