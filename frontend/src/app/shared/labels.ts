/** Libellés français et couleurs des valeurs affichées dans l'interface. */

import {
  CoverageStatus, ExecutionStatus, FailedStep, ListType, ParserType, PepCategoryKind, SourceFormat,
} from '../models/api';

export const EXECUTION_STATUSES: Record<ExecutionStatus, { label: string; css: string }> = {
  RUNNING: { label: 'En cours', css: 'bg-sky-50 text-sky-700 ring-sky-600/20' },
  SUCCESS: { label: 'Réussie', css: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  FAILED: { label: 'Échec', css: 'bg-rose-50 text-rose-700 ring-rose-600/20' },
};

/** Étapes de FtmGeneratorService, dans l'ordre, et ce qui peut y échouer. */
export const FAILED_STEPS: Record<FailedStep, { label: string; hint: string }> = {
  DOWNLOAD: { label: 'Téléchargement', hint: 'Récupération du fichier depuis l\'adresse de la source (externalSourcePath).' },
  CONVERSION: { label: 'Conversion en JSON', hint: 'Lecture du fichier source par le convertisseur de son format.' },
  MAPPING: { label: 'Mapping FTM', hint: 'Lecture du fichier de mapping et application des règles JSONPath.' },
  EXPORT: { label: 'Écriture du FTM', hint: 'Écriture du fichier FTM (ftmOutputPath).' },
};

/** Taille lisible : « 19,2 Ko », « 1,3 Mo ». */
export function fileSize(bytes: number | null | undefined): string {
  if (bytes == null) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} o`;
  }
  const units = ['Ko', 'Mo', 'Go'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} ${units[unit]}`;
}

/** Durée lisible : « 850 ms », « 4,2 s », « 2 min 05 s ». */
export function duration(ms: number | null | undefined): string {
  if (ms == null) {
    return '—';
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  if (ms < 60_000) {
    return `${(ms / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} s`;
  }
  const s = Math.round(ms / 1000);
  return `${Math.floor(s / 60)} min ${String(s % 60).padStart(2, '0')} s`;
}

export const LIST_TYPES: Record<ListType, string> = {
  PEP: 'PEP',
  SANCTION: 'Sanctions',
};

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

export const SOURCE_FORMATS: SourceFormat[] = ['CSV', 'XML', 'JSON', 'HTML', 'XLSX', 'PDF'];

export const FORMAT_CSS: Record<SourceFormat, string> = {
  CSV: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  XML: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  JSON: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  HTML: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  PDF: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  XLSX: 'bg-teal-50 text-teal-700 ring-teal-600/20',
};

export const KINDS: Record<PepCategoryKind, { label: string; css: string }> = {
  OFFICIAL: { label: 'Liste officielle', css: 'bg-slate-900 text-white' },
  AMLR: { label: 'AMLR 2027', css: 'bg-violet-100 text-violet-800' },
  RISK_BASED: { label: 'Approche risques', css: 'bg-sky-100 text-sky-800' },
  EU: { label: 'Fonction UE', css: 'bg-blue-100 text-blue-800' },
  RCA: { label: 'Famille, proches', css: 'bg-amber-100 text-amber-800' },
};

export const COVERAGE_STATUSES: Record<CoverageStatus, { label: string; css: string }> = {
  COVERED: { label: 'Couverte', css: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  PARTIAL: { label: 'Partielle', css: 'bg-amber-50 text-amber-700 ring-amber-600/20' },
  NONE: { label: 'Non couverte', css: 'bg-rose-50 text-rose-700 ring-rose-600/20' },
};

/** Clés d'un enregistrement, dans l'ordre (pour @for sur un Record). */
export function keysOf<K extends string>(record: Record<K, unknown>): K[] {
  return Object.keys(record) as K[];
}
