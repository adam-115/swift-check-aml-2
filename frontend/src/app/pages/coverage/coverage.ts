import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Country, Coverage, CoverageStatus, PepCategoryKind } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { StatusBadge } from '../../shared/status-badge';

/** Libellé et couleur de chaque nature de catégorie. */
const KINDS: Record<PepCategoryKind, { label: string; css: string }> = {
  OFFICIAL: { label: 'Liste officielle', css: 'bg-slate-900 text-white' },
  AMLR: { label: 'AMLR 2027', css: 'bg-violet-100 text-violet-800' },
  RISK_BASED: { label: 'Approche risques', css: 'bg-sky-100 text-sky-800' },
  EU: { label: 'Fonction UE', css: 'bg-blue-100 text-blue-800' },
  RCA: { label: 'Famille, proches', css: 'bg-amber-100 text-amber-800' },
};

/** Libellé et couleur de chaque état de couverture. */
const STATUSES: Record<CoverageStatus, { label: string; css: string }> = {
  COVERED: { label: 'Couverte', css: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  PARTIAL: { label: 'Partielle', css: 'bg-amber-50 text-amber-700 ring-amber-600/20' },
  NONE: { label: 'Non couverte', css: 'bg-rose-50 text-rose-700 ring-rose-600/20' },
};

/**
 * Page « Couverture PEP » (/coverage) : pour chaque catégorie de la liste officielle d'un pays, les
 * fonctions, les sources qui les couvrent et le nombre de personnes générées à leur dernière exécution.
 * Une fonction est couverte quand une source active et complète qui la déclare a déjà généré son fichier.
 */
@Component({
  selector: 'app-coverage-page',
  imports: [FormsModule, RouterLink, NgClass, DatePipe, DecimalPipe, StatusBadge],
  templateUrl: './coverage.html',
})
export class CoveragePage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly countries = signal<Country[]>([]);
  protected readonly coverage = signal<Coverage | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected countryIso = 'LU';

  protected readonly kinds = KINDS;
  protected readonly statuses = STATUSES;

  /** Pourcentage de catégories officielles entièrement couvertes. */
  protected readonly officialPercent = computed(() => {
    const c = this.coverage();
    return c && c.officialCategories ? Math.round((c.coveredOfficialCategories * 100) / c.officialCategories) : 0;
  });

  /** Fonctions couvertes / total, toutes catégories confondues. */
  protected readonly functionCounts = computed(() => {
    const functions = this.coverage()?.categories.flatMap(c => c.functions) ?? [];
    return {
      covered: functions.filter(f => f.status === 'COVERED').length,
      partial: functions.filter(f => f.status === 'PARTIAL').length,
      total: functions.length,
    };
  });

  ngOnInit(): void {
    this.api.countries().subscribe({
      next: countries => this.countries.set(countries),
      error: err => this.errorMessage.set(apiError(err)),
    });
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.api.coverage(this.countryIso).subscribe({
      next: coverage => {
        this.coverage.set(coverage);
        this.isLoading.set(false);
      },
      error: err => {
        this.coverage.set(null);
        this.errorMessage.set(apiError(err));
        this.isLoading.set(false);
      },
    });
  }
}
