import { Component, DestroyRef, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Execution } from '../../models/api';
import { ApiService } from '../../services/api.service';
import { apiError } from '../../services/api-error';
import { StatusBadge } from '../../shared/status-badge';
import { STEPS, duration } from '../../shared/format';

/**
 * Page « Exécutions » (/executions, ou /executions?sourceId=12 pour une source) : historique des
 * exécutions, les plus récentes d'abord. Relue toutes les 3 secondes tant qu'une exécution est en cours.
 */
@Component({
  selector: 'app-executions-page',
  imports: [RouterLink, DatePipe, DecimalPipe, StatusBadge],
  templateUrl: './executions.html',
})
export class ExecutionsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Paramètre d'URL ?sourceId= : limite l'historique à une source. */
  readonly sourceId = input<string | undefined>();

  protected readonly executions = signal<Execution[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly steps = STEPS;
  protected readonly duration = duration;

  protected readonly sourceLabel = computed(() => {
    const first = this.executions()[0];
    return this.sourceId() && first ? first.sourceLabel || first.sourceName : null;
  });

  private pollTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Rechargement quand le filtre de l'URL change (lien depuis une autre source)
    effect(() => {
      this.sourceId();
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
    const id = this.sourceId() ? Number(this.sourceId()) : null;
    this.api.executions(id).subscribe({
      next: executions => {
        this.executions.set(executions);
        this.isLoading.set(false);
        this.errorMessage.set('');
        clearTimeout(this.pollTimer);
        if (executions.some(e => e.status === 'RUNNING')) {
          this.pollTimer = setTimeout(() => this.load(true), 3000);
        }
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(apiError(err));
      },
    });
  }

  clearFilter(): void {
    this.router.navigate(['/executions']);
  }
}
