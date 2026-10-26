import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Country, Execution, ListType, Source } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { EXECUTION_STATUSES, FORMAT_CSS, LIST_TYPES, PARSER_TYPES, duration } from '../../shared/labels';
import { RouterLink } from '@angular/router';
import { SourceDialog } from './source-dialog';

interface Notice {
  kind: 'success' | 'error';
  text: string;
}

/**
 * Page « Sources » (/sources) : liste des sources de données, création et modification dans un dialog,
 * activation, suppression, exécution de FtmGeneratorService et historique des exécutions.
 */
@Component({
  selector: 'app-sources-page',
  imports: [FormsModule, NgClass, DatePipe, RouterLink, SourceDialog, ConfirmDialog],
  templateUrl: './sources.html',
})
export class SourcesPage implements OnInit {
  private readonly api = inject(ApiService);
  /** Suivi des exécutions en cours : id de l'exécution → minuterie de rafraîchissement. */
  private readonly watched = new Map<number, ReturnType<typeof setTimeout>>();

  // Exécutions
  protected readonly executionStatuses = EXECUTION_STATUSES;
  protected readonly duration = duration;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.watched.forEach(t => clearTimeout(t)));
  }

  protected readonly countries = signal<Country[]>([]);
  protected readonly sources = signal<Source[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly notice = signal<Notice | null>(null);
  protected readonly search = signal('');
  protected readonly busy = signal<Set<number>>(new Set());

  protected countryIso = '';
  protected listType: ListType | '' = '';

  // Dialog de création / modification
  protected readonly editOpen = signal(false);
  protected readonly editing = signal<Source | null>(null);

  // Dialog de suppression
  protected readonly deleting = signal<Source | null>(null);
  protected readonly deleteBusy = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly parserTypes = PARSER_TYPES;
  protected readonly listTypes = LIST_TYPES;
  protected readonly formatCss = FORMAT_CSS;

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.sources();
    }
    return this.sources().filter(s =>
      [s.label, s.sourceName, s.description, s.externalSourcePath,
        ...s.coverage.map(c => `${c.categoryCode} ${c.label}`)]
        .some(v => (v ?? '').toLowerCase().includes(term)));
  });

  protected readonly stats = computed(() => {
    const all = this.sources();
    return {
      total: all.length,
      enabled: all.filter(s => s.enabled).length,
      pep: all.filter(s => s.listType === 'PEP').length,
      withMapping: all.filter(s => !!s.mappingFilePath).length,
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
    this.api.sources({ listType: this.listType, country: this.countryIso }).subscribe({
      next: sources => {
        this.sources.set(sources);
        this.isLoading.set(false);
        this.errorMessage.set('');
        // Exécutions lancées ailleurs ou avant le chargement de la page
        sources.filter(s => s.lastExecution?.status === 'RUNNING').forEach(s => this.watch(s.lastExecution!));
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.editOpen.set(true);
  }

  openEdit(source: Source): void {
    this.editing.set(source);
    this.editOpen.set(true);
  }

  onSaved(saved: Source): void {
    const created = !this.editing();
    this.editOpen.set(false);
    this.notice.set({
      kind: 'success',
      text: `Source « ${this.name(saved)} » ${created ? 'créée' : 'enregistrée'}.`,
    });
    this.load();
  }

  toggleEnabled(source: Source): void {
    this.setBusy(source.id, true);
    this.api.setSourceEnabled(source.id, !source.enabled).subscribe({
      next: updated => {
        this.setBusy(source.id, false);
        this.sources.update(list => list.map(s => (s.id === updated.id ? updated : s)));
        this.notice.set({ kind: 'success', text: `« ${this.name(updated)} » ${updated.enabled ? 'activée' : 'désactivée'}.` });
      },
      error: err => {
        this.setBusy(source.id, false);
        this.notice.set({ kind: 'error', text: apiError(err) });
      },
    });
  }

  /** Lance FtmGeneratorService pour la source, puis suit l'exécution jusqu'à la fin. */
  run(source: Source): void {
    this.setBusy(source.id, true);
    this.api.runSource(source.id).subscribe({
      next: execution => {
        this.setBusy(source.id, false);
        this.setLastExecution(execution);
        this.notice.set({ kind: 'success', text: `Exécution de « ${this.name(source)} » lancée.` });
        this.watch(execution);
      },
      error: err => {
        this.setBusy(source.id, false);
        this.notice.set({ kind: 'error', text: apiError(err) });
      },
    });
  }

  isRunning(source: Source): boolean {
    return source.lastExecution?.status === 'RUNNING';
  }

  private watch(execution: Execution): void {
    if (this.watched.has(execution.id)) {
      return;
    }
    const poll = () => {
      this.api.execution(execution.id).subscribe({
        next: current => {
          this.setLastExecution(current);
          if (current.status === 'RUNNING') {
            this.watched.set(execution.id, setTimeout(poll, 1500));
            return;
          }
          this.watched.delete(execution.id);
          const source = this.sources().find(s => s.id === current.sourceId);
          const label = source ? this.name(source) : current.sourceName;
          this.notice.set(current.status === 'SUCCESS'
            ? { kind: 'success', text: `« ${label} » exécutée : ${current.entityCount} entités FTM en ${duration(current.durationMs)}.` }
            : { kind: 'error', text: `Échec de l'exécution de « ${label} » : ${current.errorMessage ?? 'erreur inconnue'}` });
        },
        error: () => this.watched.set(execution.id, setTimeout(poll, 3000)),
      });
    };
    this.watched.set(execution.id, setTimeout(poll, 1000));
  }

  private setLastExecution(execution: Execution): void {
    this.sources.update(list => list.map(s => (s.id === execution.sourceId ? { ...s, lastExecution: execution } : s)));
  }

  askDelete(source: Source): void {
    this.deleteError.set(null);
    this.deleting.set(source);
  }

  confirmDelete(): void {
    const source = this.deleting();
    if (!source) {
      return;
    }
    this.deleteBusy.set(true);
    this.api.deleteSource(source.id).subscribe({
      next: () => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        this.sources.update(list => list.filter(s => s.id !== source.id));
        this.notice.set({ kind: 'success', text: `Source « ${this.name(source)} » supprimée.` });
      },
      error: err => {
        this.deleteBusy.set(false);
        this.deleteError.set(apiError(err));
      },
    });
  }

  urls(source: Source): string[] {
    return (source.externalSourcePath ?? '').split(/\s+/).filter(Boolean);
  }

  host(url?: string): string {
    if (!url) {
      return '';
    }
    try {
      return /^https?:\/\//i.test(url) ? new URL(url).host : 'Fichier sur le serveur';
    } catch {
      return '';
    }
  }

  name(source: Source): string {
    return source.label || source.sourceName;
  }

  isBusy(id: number): boolean {
    return this.busy().has(id);
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
}
