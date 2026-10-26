import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '../models/api';
import { apiError } from './api-error';

/** Erreurs d'un formulaire renvoyées par l'API : message général et erreurs par champ. */
export interface FormErrors {
  message: string | null;
  fields: Record<string, string>;
}

export const NO_ERRORS: FormErrors = { message: null, fields: {} };

/**
 * Traduit une erreur HTTP en erreurs de formulaire. Une erreur de validation (400 avec des champs) donne
 * « Vérifiez les champs signalés » et les messages sous chaque champ.
 */
export function formErrors(err: unknown): FormErrors {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiErrorBody> | null;
    const fields = body && typeof body === 'object' && body.fields ? body.fields : {};
    if (Object.keys(fields).length) {
      return { message: 'Vérifiez les champs signalés.', fields };
    }
  }
  return { message: apiError(err), fields: {} };
}
