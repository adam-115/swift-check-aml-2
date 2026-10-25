import { Component, DestroyRef, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Execution, FailedStep, FtmEntity } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { StatusBadge } from '../../shared/status-badge';
import { STEPS, duration, schemaCounts } from '../../shared/format';

/** Étapes du pipeline, dans l'ordre d'exécution. */
const PIPELINE: { step: FailedStep; label: string; hint: string }[] = [
  { step: 'CONFIGURATION', label: 'Configuration', hint: 'Fichier de mapping et options' },
  { step: 'DOWNLOAD', label: 'Téléchargement', hint: 'Fichier(s) source' },
  { step: 'CONVERSION', label: 'Conversion', hint: 'Source → JSON' },
  { step: 'MAPPING', label: 'Mapping', hint: 'JSON → entités FTM' },
  { step: 'EXPORT', label: 'Export', hint: 'Fichier NDJSON' },
];

/** Propriétés montrées en premier dans l'aperçu d'une entité. */
const MAIN_PROPERTIES = ['name', 'birthDate', 'gender', 'country', 'position', 'startDate', 'endDate', 'status', 'description'];

/**
 * Détail d'une exécution (/executions/:id) : étapes du pipeline, chiffres, erreur et trace technique,
 * et aperçu des entités du fichier FTM produit.
 */
@Component({
  selector: 'app-execution-detail-page',
  imports: [RouterLink, FormsModule, NgClass, DatePipe, DecimalPipe, StatusBadge],
  templateUrl: './execution-detail.html',
})
export class ExecutionDetailPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  /** Paramètre de route :id. */
  readonly id = input.required<string>();

  protected readonly execution = signal<Execution | null>(null);
  protected readonly entities = signal<FtmEntity[]>([]);
  protected readonly entitiesError = signal('');
  protected readonly errorMessage = signal('');
  protected readonly isLoading = signal(true);
  protected readonly showTrace = signal(false);
  protected readonly schemaFilter = signal('');
  protected readonly search = signal('');

  protected readonly pipeline = PIPELINE;
  protected readonly steps = STEPS;
  protected readonly duration = duration;

  protected readonly schemas = computed(() => schemaCounts(this.execution()?.entitiesBySchema));

  protected readonly visibleEntities = computed(() => {
    const schema = this.schemaFilter();
    const term = this.search().trim().toLowerCase();
    return this.entities().filter(e =>
      (!schema || e.schema === schema)
      && (!term || Object.values(e.properties).some(values => values.some(v => v.toLowerCase().includes(term)))));
  });

  private pollTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      this.id();
      this.load();
    });
  }

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.pollTimer));
  }

  load(silent = false): void {
    if (!silent) {
      this.isLoading.set(true);
    }
    const id = Number(this.id());
    this.api.execution(id).subscribe({
      next: execution => {
        this.execution.set(execution);
        this.isLoading.set(false);
        this.errorMessage.set('');
        clearTimeout(this.pollTimer);
        if (execution.status === 'RUNNING') {
          this.pollTimer = setTimeout(() => this.load(true), 3000);
        } else if (execution.status === 'SUCCESS' && this.entities().length === 0) {
          this.loadEntities(id);
        }
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  private loadEntities(id: number): void {
    this.api.executionEntities(id).subscribe({
      next: entities => {
        this.entities.set(entities);
        this.entitiesError.set('');
      },
      error: err => this.entitiesError.set(apiError(err)),
    });
  }

  /** État d'une étape : faite, en échec, non atteinte ou en cours. */
  stepState(step: FailedStep): 'done' | 'failed' | 'skipped' | 'running' {
    const e = this.execution();
    if (!e) {
      return 'skipped';
    }
    if (e.status === 'SUCCESS') {
      return 'done';
    }
    const index = PIPELINE.findIndex(p => p.step === step);
    if (e.status === 'RUNNING') {
      return index === 0 ? 'running' : 'skipped';
    }
    const failedIndex = PIPELINE.findIndex(p => p.step === e.failedStep);
    if (failedIndex < 0) {
      return index === PIPELINE.length - 1 ? 'failed' : 'done';
    }
    return index < failedIndex ? 'done' : index === failedIndex ? 'failed' : 'skipped';
  }

  /** Nom affiché d'une entité. */
  caption(entity: FtmEntity): string {
    return entity.properties['name']?.[0] ?? entity.properties['description']?.[0] ?? entity.id.slice(0, 12);
  }

  /** Propriétés principales d'une entité, sans le nom. */
  summary(entity: FtmEntity): { key: string; value: string }[] {
    const keys = Object.keys(entity.properties)
      .filter(k => k !== 'name' && k !== 'sourceUrl' && k !== 'topics')
      .sort((a, b) => {
        const ia = MAIN_PROPERTIES.indexOf(a);
        const ib = MAIN_PROPERTIES.indexOf(b);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
      });
    return keys.slice(0, 6).map(key => ({ key, value: entity.properties[key].join(', ') }));
  }

  schemaCss(schema: string): string {
    switch (schema) {
      case 'Person': return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20';
      case 'Position': return 'bg-sky-50 text-sky-700 ring-sky-600/20';
      case 'Occupancy': return 'bg-violet-50 text-violet-700 ring-violet-600/20';
      default: return 'bg-slate-100 text-slate-600 ring-slate-500/20';
    }
  }
}
