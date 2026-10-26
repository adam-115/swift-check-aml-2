import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Country, Coverage, CoverageSource } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { COVERAGE_STATUSES, KINDS } from '../../shared/labels';

/**
 * Page « Couverture PEP » (/coverage) : pour un pays, chaque catégorie et chaque fonction PEP avec les sources
 * qui déclarent la couvrir. Lecture seule : la couverture se déclare dans le dialog d'une source.
 */
@Component({
  selector: 'app-coverage-page',
  imports: [FormsModule, NgClass, RouterLink],
  templateUrl: './coverage.html',
})
export class CoveragePage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly countries = signal<Country[]>([]);
  protected readonly coverage = signal<Coverage | null>(null);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal('');
  protected countryIso = 'LU';

  protected readonly kinds = KINDS;
  protected readonly statuses = COVERAGE_STATUSES;

  protected readonly percent = computed(() => {
    const c = this.coverage();
    return c && c.totalFunctions ? Math.round(100 * c.coveredFunctions / c.totalFunctions) : 0;
  });

  ngOnInit(): void {
    this.api.countries().subscribe({
      next: countries => this.countries.set(countries),
      error: err => this.errorMessage.set(apiError(err)),
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.coverage(this.countryIso).subscribe({
      next: coverage => {
        this.coverage.set(coverage);
        this.loading.set(false);
      },
      error: err => {
        this.errorMessage.set(apiError(err));
        this.loading.set(false);
      },
    });
  }

  sourceTitle(s: CoverageSource): string {
    return (s.completeness === 'COMPLETE' ? 'Complète' : 'Partielle')
      + (s.includesFormer ? ', anciens titulaires inclus' : '')
      + (s.enabled ? '' : ', source désactivée');
  }
}
