import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '../models/api';

/** Message lisible à partir d'une erreur HTTP : celui de l'API (ApiError) quand il existe. */
export function apiError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiErrorBody> | null;
    if (body && typeof body === 'object' && body.message) {
      const fields = body.fields ? Object.entries(body.fields).map(([f, m]) => `${f} : ${m}`).join(', ') : '';
      return fields ? `${body.message} (${fields})` : body.message;
    }
    if (err.status === 0) {
      return 'Le serveur ne répond pas : vérifiez que l\'application back est démarrée.';
    }
    return `Erreur ${err.status} : ${err.statusText || 'réponse inattendue du serveur'}`;
  }
  return err instanceof Error ? err.message : 'Erreur inattendue';
}
