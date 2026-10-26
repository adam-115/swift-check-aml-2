import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EntityPage, Execution, FailedStep } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { EXECUTION_STATUSES, FAILED_STEPS, duration, fileSize, keysOf } from '../../shared/labels';

/** Couleur d'un schéma FTM. */
const SCHEMA_CSS: Record<string, string> = {
  Person: 'bg-violet-100 text-violet-800',
  Position: 'bg-sky-100 text-sky-800',
  Occupancy: 'bg-amber-100 text-amber-800',
  Organization: 'bg-teal-100 text-teal-800',
  Company: 'bg-teal-100 text-teal-800',
  PublicBody: 'bg-teal-100 text-teal-800',
};

/**
 * Page « Détail d'une exécution » (/executions/:id) : résumé, fichiers à télécharger, cause de l'échec avec
 * l'étape en cause et la pile d'appels, et entités FTM produites (filtrables par schéma et par texte).
 */
@Component({
  selector: 'app-execution-detail-page',
  imports: [FormsModule, NgClass, DatePipe, RouterLink],
  templateUrl: './execution-detail.html',
})
export class ExecutionDetailPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  /** Paramètre :id de l'adresse. */
  readonly id = input.required<string>();

  protected readonly execution = signal<Execution | null>(null);
  protected readonly errorMessage = signal('');
  protected readonly entities = signal<EntityPage | null>(null);
  protected readonly entitiesLoading = signal(false);
  protected readonly entitiesError = signal('');
  protected readonly relaunching = signal(false);
  protected readonly showStack = signal(false);

  protected schema = '';
  protected query = '';
  protected page = 0;
  protected readonly pageSize = 50;

  protected readonly statuses = EXECUTION_STATUSES;
  protected readonly steps = FAILED_STEPS;
  protected readonly stepKeys = keysOf(FAILED_STEPS);
  protected readonly duration = duration;
  protected readonly size = fileSize;

  /** Schémas présents, du plus nombreux au moins nombreux. */
  protected readonly schemaTabs = computed(() =>
    Object.entries(this.entities()?.schemas ?? {}).sort((a, b) => b[1] - a[1]));

  private timer: ReturnType<typeof setTimeout> | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.stopPolling();
      this.execution.set(null);
      this.entities.set(null);
      this.schema = '';
      this.query = '';
      this.page = 0;
      this.load(id);
    });
    inject(DestroyRef).onDestroy(() => {
      this.stopPolling();
      if (this.searchTimer) {
        clearTimeout(this.searchTimer);
      }
    });
  }

  private load(id: number): void {
    this.api.execution(id).subscribe({
      next: execution => {
        this.execution.set(execution);
        this.errorMessage.set('');
        if (execution.status === 'RUNNING') {
          this.timer = setTimeout(() => this.load(id), 1500);
        } else if (this.ftmAvailable()) {
          this.loadEntities();
        }
      },
      error: err => this.errorMessage.set(apiError(err)),
    });
  }

  loadEntities(): void {
    const execution = this.execution();
    if (!execution) {
      return;
    }
    this.entitiesLoading.set(true);
    this.api.executionEntities(execution.id, { schema: this.schema, q: this.query.trim(), page: this.page, size: this.pageSize })
      .subscribe({
        next: page => {
          this.entitiesLoading.set(false);
          this.entitiesError.set('');
          this.entities.set(page);
        },
        error: err => {
          this.entitiesLoading.set(false);
          this.entitiesError.set(apiError(err));
        },
      });
  }

  selectSchema(schema: string): void {
    this.schema = schema;
    this.page = 0;
    this.loadEntities();
  }

  /** Recherche lancée 300 ms après la dernière frappe. */
  searchChanged(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.page = 0;
      this.loadEntities();
    }, 300);
  }

  goToPage(page: number): void {
    this.page = page;
    this.loadEntities();
  }

  /** Clic sur une entité citée : recherche de son libellé. */
  searchFor(caption: string): void {
    this.schema = '';
    this.query = caption;
    this.page = 0;
    this.loadEntities();
  }

  relaunch(): void {
    const execution = this.execution();
    if (!execution) {
      return;
    }
    this.relaunching.set(true);
    this.api.runSource(execution.sourceId).subscribe({
      next: started => {
        this.relaunching.set(false);
        this.router.navigate(['/executions', started.id]);
      },
      error: err => {
        this.relaunching.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  ftmAvailable(): boolean {
    return this.execution()?.files.find(f => f.kind === 'ftm')?.size != null;
  }

  /** État de chaque étape : faite, en échec, non atteinte. */
  stepState(step: FailedStep): 'done' | 'failed' | 'skipped' {
    const execution = this.execution();
    if (!execution || execution.status === 'SUCCESS') {
      return 'done';
    }
    const failed = execution.failedStep;
    if (!failed) {
      return 'skipped';
    }
    const order = this.stepKeys.indexOf(step) - this.stepKeys.indexOf(failed);
    return order < 0 ? 'done' : order === 0 ? 'failed' : 'skipped';
  }

  /** Erreurs de la chaîne, de l'exception levée à la cause première. */
  errorChain(): string[] {
    return (this.execution()?.errorMessage ?? '').split('\n← ').filter(Boolean);
  }

  schemaCss(schema: string): string {
    return SCHEMA_CSS[schema] ?? 'bg-slate-100 text-slate-700';
  }

  properties(item: { properties: Record<string, string[]> }): [string, string[]][] {
    return Object.entries(item.properties);
  }

  lastItem(page: EntityPage): number {
    return Math.min((page.page + 1) * page.size, page.matching);
  }

  private stopPolling(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
