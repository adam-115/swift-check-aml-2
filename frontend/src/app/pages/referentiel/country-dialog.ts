import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Country, CountryRequest } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { FormErrors, NO_ERRORS, formErrors } from '../../services/form-errors';
import { Dialog } from '../../shared/dialog';

/** Dialog de création ou de modification d'un pays ({@link country} vide : création). */
@Component({
  selector: 'app-country-dialog',
  imports: [FormsModule, Dialog],
  template: `
    <app-dialog [open]="open()" [busy]="busy()" (closed)="closed.emit()"
                [title]="isEdit() ? 'Modifier le pays' : 'Nouveau pays'" [subtitle]="country()?.name">
      @if (errors().message) {
        <div class="mb-4 bg-rose-50 border border-rose-200 text-rose-800 p-3 rounded-xl text-sm">{{ errors().message }}</div>
      }
      <form id="country-form" (ngSubmit)="save()" class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="iso">Code ISO *</label>
          <input id="iso" name="iso" [(ngModel)]="form.iso" maxlength="2" [readonly]="isEdit()" placeholder="LU"
                 class="w-full rounded-lg border px-3 py-2 font-mono uppercase focus:outline-none focus:ring-2 focus:ring-emerald-500 read-only:bg-slate-100"
                 [class.border-rose-400]="err('iso')" [class.border-slate-300]="!err('iso')">
          @if (err('iso'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="name">Nom *</label>
          <input id="name" name="name" [(ngModel)]="form.name"
                 class="w-full rounded-lg border px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                 [class.border-rose-400]="err('name')" [class.border-slate-300]="!err('name')">
          @if (err('name'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="retentionMonths">Rétention après la fin des fonctions (mois) *</label>
          <input id="retentionMonths" name="retentionMonths" type="number" min="0" max="240" [(ngModel)]="form.retentionMonths"
                 class="w-full rounded-lg border px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                 [class.border-rose-400]="err('retentionMonths')" [class.border-slate-300]="!err('retentionMonths')">
          @if (err('retentionMonths'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div class="flex items-center gap-2 pt-6">
          <input id="euMember" name="euMember" type="checkbox" [(ngModel)]="form.euMember"
                 class="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500">
          <label for="euMember" class="text-sm text-slate-700">État membre de l'UE</label>
        </div>
        <div class="sm:col-span-2">
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="legalBasis">Base légale des PEP</label>
          <input id="legalBasis" name="legalBasis" [(ngModel)]="form.legalBasis"
                 class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
          @if (err('legalBasis'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="officialListReference">Liste officielle des fonctions PEP</label>
          <input id="officialListReference" name="officialListReference" [(ngModel)]="form.officialListReference" placeholder="JO C/2023/724"
                 class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="officialListDate">Date de la liste</label>
          <input id="officialListDate" name="officialListDate" type="date" [(ngModel)]="form.officialListDate"
                 class="w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div class="sm:col-span-2">
          <label class="block text-xs font-semibold text-slate-600 mb-1" for="officialListUrl">Adresse de la liste officielle</label>
          <input id="officialListUrl" name="officialListUrl" [(ngModel)]="form.officialListUrl"
                 class="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-emerald-500">
          @if (err('officialListUrl'); as e) { <p class="mt-1 text-xs text-rose-600">{{ e }}</p> }
        </div>
      </form>
      <div dialog-footer class="contents">
        <button type="button" (click)="closed.emit()" [disabled]="busy()"
                class="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Annuler</button>
        <button type="submit" form="country-form" [disabled]="busy()"
                class="rounded-lg bg-emerald-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50">
          {{ busy() ? 'Enregistrement…' : (isEdit() ? 'Enregistrer' : 'Créer le pays') }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class CountryDialog {
  private readonly api = inject(ApiService);

  readonly open = input(false);
  readonly country = input<Country | null>(null);
  readonly saved = output<Country>();
  readonly closed = output<void>();

  protected readonly busy = signal(false);
  protected readonly errors = signal<FormErrors>(NO_ERRORS);
  protected readonly isEdit = computed(() => !!this.country());
  protected form: CountryRequest = this.empty();

  constructor() {
    effect(() => {
      if (this.open()) {
        const c = this.country();
        this.form = c ? { ...c, officialListDate: c.officialListDate ?? null } : this.empty();
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
    const request: CountryRequest = { ...this.form, iso: (this.form.iso ?? '').toUpperCase(),
      officialListDate: this.form.officialListDate || null };
    const c = this.country();
    (c ? this.api.updateCountry(c.iso, request) : this.api.createCountry(request)).subscribe({
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

  private empty(): CountryRequest {
    return { iso: '', name: '', euMember: true, legalBasis: '', retentionMonths: 12, officialListReference: '',
      officialListDate: null, officialListUrl: '' };
  }
}
