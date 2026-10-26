import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Completeness, Country, PepFunction, Source, SourceRequest } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { FormErrors, NO_ERRORS, formErrors } from '../../services/form-errors';
import { apiError } from '../../services/api-error';
import { Dialog } from '../../shared/dialog';
import { KINDS, LIST_TYPES, PARSER_TYPES, SOURCE_FORMATS, keysOf } from '../../shared/labels';

/** Choix de couverture pour une fonction PEP dans le formulaire. */
interface CoverageChoice {
  selected: boolean;
  completeness: Completeness;
  includesFormer: boolean;
}

type Tab = 'general' | 'files' | 'coverage';

/**
 * Dialog de création ou de modification d'une source ({@link source} vide : création).
 * Les erreurs de validation du serveur s'affichent sous chaque champ ; l'onglet en erreur est signalé.
 */
@Component({
  selector: 'app-source-dialog',
  imports: [FormsModule, Dialog],
  templateUrl: './source-dialog.html',
})
export class SourceDialog {
  private readonly api = inject(ApiService);

  readonly open = input(false);
  readonly source = input<Source | null>(null);
  readonly countries = input<Country[]>([]);
  readonly saved = output<Source>();
  readonly closed = output<void>();

  protected readonly tab = signal<Tab>('general');
  protected readonly busy = signal(false);
  protected readonly errors = signal<FormErrors>(NO_ERRORS);
  protected readonly functions = signal<PepFunction[]>([]);
  protected readonly functionsError = signal('');
  protected readonly choices = signal<Record<number, CoverageChoice>>({});

  protected form: SourceRequest = this.empty();

  protected readonly listTypes = keysOf(LIST_TYPES);
  protected readonly listTypeLabels = LIST_TYPES;
  protected readonly parserTypes = keysOf(PARSER_TYPES);
  protected readonly parserTypeLabels = PARSER_TYPES;
  protected readonly formats = SOURCE_FORMATS;
  protected readonly kinds = KINDS;

  protected readonly isEdit = computed(() => !!this.source());

  /** Fonctions regroupées par catégorie, dans l'ordre du référentiel. */
  protected readonly groups = computed(() => {
    const groups: { code: string; label: string; kind: PepFunction['categoryKind']; functions: PepFunction[] }[] = [];
    for (const f of this.functions()) {
      let group = groups.find(g => g.code === f.categoryCode);
      if (!group) {
        group = { code: f.categoryCode, label: f.categoryLabel, kind: f.categoryKind, functions: [] };
        groups.push(group);
      }
      group.functions.push(f);
    }
    return groups;
  });

  protected readonly selectedCount = computed(() => Object.values(this.choices()).filter(c => c.selected).length);

  /** Champs de chaque onglet, pour signaler l'onglet qui contient une erreur. */
  private static readonly TAB_FIELDS: Record<Tab, string[]> = {
    general: ['sourceName', 'label', 'description', 'attribution', 'sourceFormat', 'listType', 'parserType',
      'countryIso', 'pepFunctionId', 'retentionMonths'],
    files: ['externalSourcePath', 'localSourcePath', 'initialJsonSourcePath', 'mappingFilePath', 'ftmOutputPath'],
    coverage: ['coverage'],
  };

  constructor() {
    // À chaque ouverture : formulaire rempli avec la source, ou vide pour une création
    effect(() => {
      if (!this.open()) {
        return;
      }
      const s = this.source();
      this.form = s ? this.fromSource(s) : this.empty();
      const choices: Record<number, CoverageChoice> = {};
      for (const c of s?.coverage ?? []) {
        choices[c.functionId] = { selected: true, completeness: c.completeness, includesFormer: c.includesFormer };
      }
      this.choices.set(choices);
      this.errors.set(NO_ERRORS);
      this.tab.set('general');
      this.loadFunctions();
    });
  }

  loadFunctions(): void {
    const iso = this.form.countryIso;
    if (!iso) {
      this.functions.set([]);
      return;
    }
    this.api.functions(iso).subscribe({
      next: functions => {
        this.functions.set(functions);
        this.functionsError.set('');
        // Une fonction principale d'un autre pays ne peut plus être choisie
        if (this.form.pepFunctionId && !functions.some(f => f.id === this.form.pepFunctionId)) {
          this.form.pepFunctionId = null;
        }
      },
      error: err => this.functionsError.set(apiError(err)),
    });
  }

  choice(id: number): CoverageChoice {
    return this.choices()[id] ?? { selected: false, completeness: 'COMPLETE', includesFormer: false };
  }

  updateChoice(id: number, change: Partial<CoverageChoice>): void {
    this.choices.update(all => ({ ...all, [id]: { ...this.choice(id), ...change } }));
  }

  /** La fonction principale est aussi cochée dans la couverture. */
  setMainFunction(id: number | null): void {
    this.form.pepFunctionId = id;
    if (id && !this.choice(id).selected) {
      this.updateChoice(id, { selected: true });
    }
  }

  tabHasError(tab: Tab): boolean {
    const fields = Object.keys(this.errors().fields);
    return fields.some(f => SourceDialog.TAB_FIELDS[tab].some(t => f === t || f.startsWith(t + '[')));
  }

  fieldError(name: string): string | null {
    return this.errors().fields[name] ?? null;
  }

  save(): void {
    const request: SourceRequest = {
      ...this.form,
      countryIso: this.form.countryIso || null,
      pepFunctionId: this.form.pepFunctionId || null,
      listType: this.form.listType || null,
      parserType: this.form.parserType || null,
      coverage: Object.entries(this.choices())
        .filter(([, c]) => c.selected)
        .map(([id, c]) => ({ functionId: Number(id), completeness: c.completeness, includesFormer: c.includesFormer })),
    };
    this.busy.set(true);
    this.errors.set(NO_ERRORS);
    const s = this.source();
    const call = s ? this.api.updateSource(s.id, request) : this.api.createSource(request);
    call.subscribe({
      next: saved => {
        this.busy.set(false);
        this.saved.emit(saved);
      },
      error: err => {
        this.busy.set(false);
        const errors = formErrors(err);
        this.errors.set(errors);
        const firstTab = (['general', 'files', 'coverage'] as Tab[]).find(t => this.tabHasError(t));
        if (firstTab) {
          this.tab.set(firstTab);
        }
      },
    });
  }

  private empty(): SourceRequest {
    return {
      sourceName: '', label: '', description: '', attribution: '', sourceFormat: 'CSV', listType: 'PEP',
      parserType: 'OPEN_DATA_FILE', countryIso: 'LU', pepFunctionId: null, retentionMonths: 12, enabled: true,
      externalSourcePath: '',
      localSourcePath: '', initialJsonSourcePath: '', mappingFilePath: '', ftmOutputPath: '', coverage: [],
    };
  }

  private fromSource(s: Source): SourceRequest {
    return {
      sourceName: s.sourceName, label: s.label ?? '', description: s.description ?? '',
      attribution: s.attribution ?? '', sourceFormat: s.sourceFormat, listType: s.listType ?? null,
      parserType: s.parserType ?? null, countryIso: s.countryIso ?? '', pepFunctionId: s.pepFunctionId ?? null,
      retentionMonths: s.retentionMonths ?? null,
      enabled: s.enabled, externalSourcePath: s.externalSourcePath ?? '', localSourcePath: s.localSourcePath ?? '',
      initialJsonSourcePath: s.initialJsonSourcePath ?? '', mappingFilePath: s.mappingFilePath ?? '',
      ftmOutputPath: s.ftmOutputPath ?? '', coverage: [],
    };
  }
}
