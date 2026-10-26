import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Execution, ExecutionStatus, Source } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { EXECUTION_STATUSES, FAILED_STEPS, duration, keysOf } from '../../shared/labels';

/**
 * Page « Exécutions » (/executions?source=<id>) : historique des exécutions, toutes sources ou d'une source,
 * filtré par état. Une ligne ouvre le détail de l'exécution. Se rafraîchit tant qu'une exécution est en cours.
 */
@Component({
  selector: 'app-executions-page',
  imports: [FormsModule, NgClass, DatePipe, RouterLink],
  templateUrl: './executions.html',
})
export class ExecutionsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  /** Paramètre ?source= de l'adresse. */
  readonly source = input<string | undefined>();

  protected readonly sources = signal<Source[]>([]);
  protected readonly executions = signal<Execution[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal('');
  protected sourceId: number | null = null;
  protected status: ExecutionStatus | '' = '';

  protected readonly statuses = EXECUTION_STATUSES;
  protected readonly statusKeys = keysOf(EXECUTION_STATUSES);
  protected readonly steps = FAILED_STEPS;
  protected readonly duration = duration;

  protected readonly selectedSource = computed(() => this.sources().find(s => s.id === this.currentSource()) ?? null);
  private readonly currentSource = signal<number | null>(null);

  protected readonly stats = computed(() => {
    const all = this.executions();
    return {
      total: all.length,
      success: all.filter(e => e.status === 'SUCCESS').length,
      failed: all.filter(e => e.status === 'FAILED').length,
      running: all.filter(e => e.status === 'RUNNING').length,
    };
  });

  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    this.sourceId = this.source() ? Number(this.source()) : null;
    this.currentSource.set(this.sourceId);
    this.api.sources({}).subscribe({
      next: sources => this.sources.set(sources),
      error: err => this.errorMessage.set(apiError(err)),
    });
    this.load();
  }

  /** Filtre changé : l'adresse garde la source choisie. */
  filter(): void {
    this.currentSource.set(this.sourceId);
    this.router.navigate([], { queryParams: { source: this.sourceId || null }, replaceUrl: true });
    this.load();
  }

  load(): void {
    this.stopPolling();
    this.api.allExecutions({ sourceId: this.sourceId, status: this.status }).subscribe({
      next: executions => {
        this.loading.set(false);
        this.errorMessage.set('');
        this.executions.set(executions);
        if (executions.some(e => e.status === 'RUNNING')) {
          this.timer = setTimeout(() => this.load(), 2000);
        }
      },
      error: err => {
        this.loading.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  open(execution: Execution): void {
    this.router.navigate(['/executions', execution.id]);
  }

  /** Première ligne de la cause, pour le tableau. */
  causeLine(execution: Execution): string {
    return (execution.errorCause ?? execution.errorMessage ?? '').split('\n')[0];
  }

  private stopPolling(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
