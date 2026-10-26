import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { Country, PepCategory, PepFunction } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { KINDS } from '../../shared/labels';
import { CategoryDialog } from './category-dialog';
import { CountryDialog } from './country-dialog';
import { FunctionDialog } from './function-dialog';

type Tab = 'countries' | 'categories' | 'functions';

/** Suppression en attente de confirmation. */
interface PendingDelete {
  title: string;
  message: string;
  run: () => Observable<void>;
  done: string;
}

/**
 * Page « Référentiel PEP » (/referentiel) : pays, catégories et fonctions PEP, créés et modifiés dans des
 * dialogs. Un élément encore utilisé ne peut pas être supprimé : le serveur l'explique dans la confirmation.
 */
@Component({
  selector: 'app-referentiel-page',
  imports: [FormsModule, NgClass, DatePipe, ConfirmDialog, CountryDialog, CategoryDialog, FunctionDialog],
  templateUrl: './referentiel.html',
})
export class ReferentielPage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly tab = signal<Tab>('categories');
  protected readonly countries = signal<Country[]>([]);
  protected readonly categories = signal<PepCategory[]>([]);
  protected readonly functions = signal<PepFunction[]>([]);
  protected readonly errorMessage = signal('');
  protected readonly notice = signal<string | null>(null);
  protected readonly search = signal('');
  protected countryIso = 'LU';

  protected readonly kinds = KINDS;

  // Dialogs
  protected readonly countryOpen = signal(false);
  protected readonly editingCountry = signal<Country | null>(null);
  protected readonly categoryOpen = signal(false);
  protected readonly editingCategory = signal<PepCategory | null>(null);
  protected readonly functionOpen = signal(false);
  protected readonly editingFunction = signal<PepFunction | null>(null);
  protected readonly functionCategoryId = signal<number | null>(null);

  protected readonly pendingDelete = signal<PendingDelete | null>(null);
  protected readonly deleteBusy = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly filteredFunctions = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.functions().filter(f => !term
      || [f.label, f.institution, f.categoryCode, f.categoryLabel].some(v => (v ?? '').toLowerCase().includes(term)));
  });

  protected readonly countryName = computed(() =>
    this.countries().find(c => c.iso === this.countryIso)?.name ?? this.countryIso);

  ngOnInit(): void {
    this.loadCountries();
    this.loadCountryData();
  }

  loadCountries(): void {
    this.api.countries().subscribe({
      next: countries => this.countries.set(countries),
      error: err => this.errorMessage.set(apiError(err)),
    });
  }

  loadCountryData(): void {
    this.api.categories(this.countryIso).subscribe({
      next: categories => this.categories.set(categories),
      error: err => this.errorMessage.set(apiError(err)),
    });
    this.api.functions(this.countryIso).subscribe({
      next: functions => this.functions.set(functions),
      error: err => this.errorMessage.set(apiError(err)),
    });
  }

  // ------------------------------------------------------------------ Pays

  newCountry(): void {
    this.editingCountry.set(null);
    this.countryOpen.set(true);
  }

  editCountry(country: Country): void {
    this.editingCountry.set(country);
    this.countryOpen.set(true);
  }

  countrySaved(country: Country): void {
    this.countryOpen.set(false);
    this.notice.set(`Pays « ${country.name} » enregistré.`);
    this.loadCountries();
  }

  deleteCountry(country: Country): void {
    this.askDelete({
      title: 'Supprimer le pays',
      message: `Le pays « ${country.name} » (${country.iso}) sera supprimé.`,
      run: () => this.api.deleteCountry(country.iso),
      done: `Pays « ${country.name} » supprimé.`,
    });
  }

  // ------------------------------------------------------------ Catégories

  newCategory(): void {
    this.editingCategory.set(null);
    this.categoryOpen.set(true);
  }

  editCategory(category: PepCategory): void {
    this.editingCategory.set(category);
    this.categoryOpen.set(true);
  }

  categorySaved(category: PepCategory): void {
    this.categoryOpen.set(false);
    this.notice.set(`Catégorie « ${category.code} » enregistrée.`);
    if (category.countryIso !== this.countryIso) {
      this.countryIso = category.countryIso;
    }
    this.loadCountryData();
  }

  deleteCategory(category: PepCategory): void {
    this.askDelete({
      title: 'Supprimer la catégorie',
      message: `La catégorie « ${category.code} · ${category.label} » sera supprimée.`,
      run: () => this.api.deleteCategory(category.id),
      done: `Catégorie « ${category.code} » supprimée.`,
    });
  }

  // ------------------------------------------------------------- Fonctions

  newFunction(categoryId: number | null = null): void {
    this.editingFunction.set(null);
    this.functionCategoryId.set(categoryId ?? this.categories()[0]?.id ?? null);
    this.functionOpen.set(true);
  }

  editFunction(fn: PepFunction): void {
    this.editingFunction.set(fn);
    this.functionOpen.set(true);
  }

  functionSaved(fn: PepFunction): void {
    this.functionOpen.set(false);
    this.notice.set(`Fonction « ${fn.label} » enregistrée.`);
    this.loadCountryData();
  }

  deleteFunction(fn: PepFunction): void {
    this.askDelete({
      title: 'Supprimer la fonction',
      message: `La fonction « ${fn.label} » (${fn.categoryCode}) sera supprimée.`,
      run: () => this.api.deleteFunction(fn.id),
      done: `Fonction « ${fn.label} » supprimée.`,
    });
  }

  // ----------------------------------------------------------- Suppression

  private askDelete(pending: PendingDelete): void {
    this.deleteError.set(null);
    this.pendingDelete.set(pending);
  }

  confirmDelete(): void {
    const pending = this.pendingDelete();
    if (!pending) {
      return;
    }
    this.deleteBusy.set(true);
    pending.run().subscribe({
      next: () => {
        this.deleteBusy.set(false);
        this.pendingDelete.set(null);
        this.notice.set(pending.done);
        this.loadCountries();
        this.loadCountryData();
      },
      error: err => {
        this.deleteBusy.set(false);
        this.deleteError.set(apiError(err));
      },
    });
  }
}
