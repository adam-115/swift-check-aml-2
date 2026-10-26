import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CategoryRequest, Country, PepCategory } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { FormErrors, NO_ERRORS, formErrors } from '../../services/form-errors';
import { Dialog } from '../../shared/dialog';
import { KINDS, keysOf } from '../../shared/labels';

/** Dialog de création ou de modification d'une catégorie PEP ({@link category} vide : création). */
@Component({
  selector: 'app-category-dialog',
  imports: [FormsModule, Dialog],
  template: `
    <app-dialog [open]="open()" [busy]="busy()" (closed)="closed.emit()"
                [title]="isEdit() ? 'Modifier la catégorie' : 'Nouvelle catégorie PEP'" [subtitle]="category()?.code">
      @if (errors().message) {
        <div class="mb-4 bg-rose-50 border border-rose-200 text-rose-800 p-3 rounded-xl text-sm">{{ errors().message }}</div>
      }
      <form id="category-form" (ngSubmit)="save()" class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="countryIso">Pays *</label>
          <select id="countryIso" name="countryIso" [(ngModel)]="form.countryIso"
                  class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            @for (c of countries(); track c.iso) { <option [value]="c.iso">{{ c.name }} ({{ c.iso }})</option> }
          </select>
          @if (err('countryIso'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="code">Code *</label>
          <input id="code" name="code" [(ngModel)]="form.code" placeholder="LU-b"
                 class="w-full rounded-lg border px-3 py-2 font-mono focus:outline-none focus:ring-2 focus:ring-emerald-500"
                 [class.border-rose-400]="err('code')" [class.border-slate-300]="!err('code')">
          @if (err('code'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div class="sm:col-span-2">
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="label">Libellé *</label>
          <input id="label" name="label" [(ngModel)]="form.label"
                 class="w-full rounded-lg border px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                 [class.border-rose-400]="err('label')" [class.border-slate-300]="!err('label')">
          @if (err('label'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="kind">Nature *</label>
          <select id="kind" name="kind" [(ngModel)]="form.kind"
                  class="w-full rounded-lg border bg-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  [class.border-rose-400]="err('kind')" [class.border-slate-300]="!err('kind')">
            @for (k of kindKeys; track k) { <option [ngValue]="k">{{ kinds[k].label }}</option> }
          </select>
          @if (err('kind'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1" for="riskClass">Classe de risque *</label>
            <select id="riskClass" name="riskClass" [(ngModel)]="form.riskClass"
                    class="w-full rounded-lg border bg-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    [class.border-rose-400]="err('riskClass')" [class.border-slate-300]="!err('riskClass')">
              @for (r of [1, 2, 3, 4]; track r) { <option [ngValue]="r">{{ r }}{{ r === 1 ? ' (la plus élevée)' : r === 4 ? ' (la plus faible)' : '' }}</option> }
            </select>
            @if (err('riskClass'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1" for="sortOrder">Ordre</label>
            <input id="sortOrder" name="sortOrder" type="number" min="0" [(ngModel)]="form.sortOrder"
                   class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            @if (err('sortOrder'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
          </div>
        </div>
        <div class="sm:col-span-2">
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="legalReference">Texte de référence</label>
          <input id="legalReference" name="legalReference" [(ngModel)]="form.legalReference"
                 placeholder="Directive (UE) 2015/849, art. 3, point 9 b)"
                 class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
          @if (err('legalReference'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
      </form>
      <div dialog-footer class="contents">
        <button type="button" (click)="closed.emit()" [disabled]="busy()"
                class="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Annuler</button>
        <button type="submit" form="category-form" [disabled]="busy()"
                class="rounded-lg bg-emerald-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50">
          {{ busy() ? 'Enregistrement…' : (isEdit() ? 'Enregistrer' : 'Créer la catégorie') }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class CategoryDialog {
  private readonly api = inject(ApiService);

  readonly open = input(false);
  readonly category = input<PepCategory | null>(null);
  readonly countries = input<Country[]>([]);
  /** Pays proposé pour une nouvelle catégorie. */
  readonly defaultCountry = input('LU');
  readonly saved = output<PepCategory>();
  readonly closed = output<void>();

  protected readonly busy = signal(false);
  protected readonly errors = signal<FormErrors>(NO_ERRORS);
  protected readonly isEdit = computed(() => !!this.category());
  protected readonly kinds = KINDS;
  protected readonly kindKeys = keysOf(KINDS);
  protected form: CategoryRequest = this.empty('LU');

  constructor() {
    effect(() => {
      if (this.open()) {
        const c = this.category();
        this.form = c
          ? { countryIso: c.countryIso, code: c.code, label: c.label, kind: c.kind, riskClass: c.riskClass,
              sortOrder: c.sortOrder, legalReference: c.legalReference ?? '' }
          : this.empty(this.defaultCountry());
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
    const c = this.category();
    (c ? this.api.updateCategory(c.id, this.form) : this.api.createCategory(this.form)).subscribe({
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

  private empty(countryIso: string): CategoryRequest {
    return { countryIso, code: '', label: '', kind: 'OFFICIAL', riskClass: 2, sortOrder: 0, legalReference: '' };
  }
}
