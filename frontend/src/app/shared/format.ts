/** Petites fonctions d'affichage partagées par les écrans. */

import { FailedStep, ParserType, SourceFormat } from '../models/api';

/** Durée lisible entre deux dates : « 42 s », « 2 min 05 s ». */
export function duration(start?: string | null, end?: string | null): string {
  if (!start) {
    return '—';
  }
  const stop = end ? new Date(end).getTime() : Date.now();
  return elapsed(stop - new Date(start).getTime());
}

/** Durée lisible en millisecondes. */
export function elapsed(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000));
  return s < 60 ? `${s} s` : `${Math.floor(s / 60)} min ${String(s % 60).padStart(2, '0')} s`;
}

/** Adresses d'une source : une par ligne. */
export function sourceUrls(external?: string | null): string[] {
  return (external ?? '').split(/\s+/).filter(Boolean);
}

/** Hôte d'une URL, ou « Fichier sur le serveur » pour un chemin. */
export function sourceHost(origin?: string | null): string {
  if (!origin) {
    return '';
  }
  if (origin.startsWith('classpath:')) {
    return 'Fichier de référence intégré';
  }
  try {
    return /^https?:\/\//i.test(origin) ? new URL(origin).host : 'Fichier sur le serveur';
  } catch {
    return '';
  }
}

/** Type de source T1 à T7. */
export const PARSER_TYPES: Record<ParserType, { code: string; label: string }> = {
  OPEN_DATA_FILE: { code: 'T1', label: 'Fichier open data' },
  API: { code: 'T2', label: 'API' },
  SPARQL: { code: 'T3', label: 'SPARQL' },
  OFFICIAL_GAZETTE: { code: 'T4', label: 'Journal officiel' },
  WEB_PAGE: { code: 'T5', label: 'Page web' },
  PDF_DOCUMENT: { code: 'T6', label: 'Document PDF' },
  DECLARATION_REGISTER: { code: 'T7', label: 'Registre officiel' },
};

/** Couleur du badge de format. */
export const FORMAT_CSS: Record<SourceFormat, string> = {
  CSV: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  XML: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  JSON: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  HTML: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  PDF: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  XLSX: 'bg-teal-50 text-teal-700 ring-teal-600/20',
};

/** Libellé de l'étape en échec. */
export const STEPS: Record<FailedStep, string> = {
  CONFIGURATION: 'Configuration',
  DOWNLOAD: 'Téléchargement',
  CONVERSION: 'Conversion en JSON',
  MAPPING: 'Mapping FTM',
  EXPORT: 'Écriture du fichier FTM',
};

/** Nombre d'entités par schéma, à partir du JSON enregistré avec l'exécution. */
export function schemaCounts(json?: string | null): { schema: string; count: number }[] {
  if (!json) {
    return [];
  }
  try {
    return Object.entries(JSON.parse(json) as Record<string, number>).map(([schema, count]) => ({ schema, count }));
  } catch {
    return [];
  }
}
