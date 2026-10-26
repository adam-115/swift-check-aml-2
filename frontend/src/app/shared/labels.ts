/** Libellés français et couleurs des valeurs affichées dans l'interface. */

import { CoverageStatus, ListType, ParserType, PepCategoryKind, SourceFormat } from '../models/api';

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
