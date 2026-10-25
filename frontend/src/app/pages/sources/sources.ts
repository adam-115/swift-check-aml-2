import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Country, ListType, Source } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { StatusBadge } from '../../shared/status-badge';
import { FORMAT_CSS, PARSER_TYPES, sourceHost, sourceUrls } from '../../shared/format';

/** Message affiché en haut de la page après une action. */
interface Notice {
  kind: 'success' | 'error';
  text: string;
}

/**
 * Page « Sources » (/sources) : chaque source configurée, la couverture PEP qu'elle déclare, sa dernière
 * exécution, et les actions (exécuter, activer / désactiver, changer l'adresse). La page se rafraîchit
 * toute seule tant qu'une exécution est en cours.
 */
@Component({
  selector: 'app-sources-page',
  imports: [FormsModule, RouterLink, NgClass, DatePipe, DecimalPipe, StatusBadge],
  templateUrl: './sources.html',
})
export class SourcesPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly countries = signal<Country[]>([]);
  protected readonly sources = signal<Source[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly notice = signal<Notice | null>(null);
  protected readonly expanded = signal<number | null>(null);
  protected readonly busy = signal<Set<number>>(new Set());

  protected countryIso = 'LU';
  protected listType: ListType | '' = 'PEP';
  protected readonly search = signal('');
  protected editedUrls = '';

  protected readonly parserTypes = PARSER_TYPES;
  protected readonly formatCss = FORMAT_CSS;
  protected readonly sourceHost = sourceHost;
  protected readonly sourceUrls = sourceUrls;

  private pollTimer?: ReturnType<typeof setTimeout>;

  /** Sources filtrées par la recherche (nom, libellé, fonction couverte). */
  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.sources();
    }
    return this.sources().filter(s =>
      [s.label, s.sourceName, s.description, ...s.coverage.map(c => `${c.categoryCode} ${c.label}`)]
        .some(v => (v ?? '').toLowerCase().includes(term)));
  });

  /** Chiffres de la synthèse. */
  protected readonly stats = computed(() => {
    const all = this.sources();
    const last = all.map(s => s.lastExecution).filter(e => !!e);
    return {
      total: all.length,
      implemented: all.filter(s => s.implemented).length,
      success: last.filter(e => e!.status === 'SUCCESS').length,
      failed: last.filter(e => e!.status === 'FAILED').length,
      running: last.filter(e => e!.status === 'RUNNING').length,
      persons: last.filter(e => e!.status === 'SUCCESS').reduce((sum, e) => sum + (e!.personCount ?? 0), 0),
    };
  });

  ngOnInit(): void {
    this.api.countries().subscribe({
      next: countries => this.countries.set(countries),
      error: err => this.errorMessage.set(apiError(err)),
    });
    this.load();
    this.destroyRef.onDestroy(() => clearTimeout(this.pollTimer));
  }

  load(silent = false): void {
    if (!silent) {
      this.isLoading.set(true);
    }
    this.api.sources({ listType: this.listType, country: this.countryIso }).subscribe({
      next: sources => {
        this.sources.set(sources);
        this.isLoading.set(false);
        this.errorMessage.set('');
        this.schedulePolling();
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  /** Tant qu'une exécution est en cours, la liste est relue toutes les 3 secondes. */
  private schedulePolling(): void {
    clearTimeout(this.pollTimer);
    if (this.sources().some(s => s.lastExecution?.status === 'RUNNING')) {
      this.pollTimer = setTimeout(() => this.load(true), 3000);
    }
  }

  run(source: Source): void {
    this.setBusy(source.id, true);
    this.notice.set(null);
    this.api.runSource(source.id).subscribe({
      next: execution => {
        this.setBusy(source.id, false);
        this.replace({ ...source, lastExecution: execution });
        this.notice.set({ kind: 'success', text: `Exécution de « ${this.name(source)} » lancée.` });
        this.schedulePolling();
      },
      error: err => {
        this.setBusy(source.id, false);
        this.notice.set({ kind: 'error', text: apiError(err) });
      },
    });
  }

  toggleEnabled(source: Source): void {
    this.setBusy(source.id, true);
    this.api.updateSource(source.id, { enabled: !source.enabled }).subscribe({
      next: updated => {
        this.setBusy(source.id, false);
        this.replace(updated);
        this.notice.set({
          kind: 'success',
          text: `« ${this.name(updated)} » ${updated.enabled ? 'activée' : 'désactivée'}.`,
        });
      },
      error: err => {
        this.setBusy(source.id, false);
        this.notice.set({ kind: 'error', text: apiError(err) });
      },
    });
  }

  toggleDetails(source: Source): void {
    if (this.expanded() === source.id) {
      this.expanded.set(null);
    } else {
      this.expanded.set(source.id);
      this.editedUrls = sourceUrls(source.externalSourcePath).join('\n');
    }
  }

  saveUrls(source: Source): void {
    this.setBusy(source.id, true);
    this.api.updateSource(source.id, { externalSourcePath: this.editedUrls }).subscribe({
      next: updated => {
        this.setBusy(source.id, false);
        this.replace(updated);
        this.notice.set({ kind: 'success', text: `Adresse de « ${this.name(updated)} » enregistrée.` });
      },
      error: err => {
        this.setBusy(source.id, false);
        this.notice.set({ kind: 'error', text: apiError(err) });
      },
    });
  }

  /** Options de conversion lisibles (JSON indenté). */
  prettyOptions(json?: string | null): string {
    if (!json) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }

  isBusy(id: number): boolean {
    return this.busy().has(id);
  }

  name(source: Source): string {
    return source.label || source.sourceName;
  }

  private setBusy(id: number, value: boolean): void {
    const next = new Set(this.busy());
    if (value) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.busy.set(next);
  }

  private replace(updated: Source): void {
    this.sources.update(list => list.map(s => (s.id === updated.id ? updated : s)));
  }
}
