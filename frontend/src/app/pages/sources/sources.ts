import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Country, ListType, Source } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { FORMAT_CSS, LIST_TYPES, PARSER_TYPES } from '../../shared/labels';
import { SourceDialog } from './source-dialog';

interface Notice {
  kind: 'success' | 'error';
  text: string;
}

/**
 * Page « Sources » (/sources) : liste des sources de données, création et modification dans un dialog,
 * activation et suppression.
 */
@Component({
  selector: 'app-sources-page',
  imports: [FormsModule, NgClass, DatePipe, SourceDialog, ConfirmDialog],
  templateUrl: './sources.html',
})
export class SourcesPage implements OnInit {
  private readonly api = inject(ApiService);

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
