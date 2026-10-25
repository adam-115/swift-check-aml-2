/** Types renvoyés par l'API REST du back (voir com.example.app.api.dto). */

export type ListType = 'SANCTION' | 'PEP';
export type ParserType =
  | 'OPEN_DATA_FILE' | 'API' | 'SPARQL' | 'OFFICIAL_GAZETTE' | 'WEB_PAGE' | 'PDF_DOCUMENT' | 'DECLARATION_REGISTER';
export type SourceFormat = 'XML' | 'JSON' | 'CSV' | 'XLSX' | 'HTML' | 'PDF';
export type Completeness = 'COMPLETE' | 'PARTIAL';
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type FailedStep = 'CONFIGURATION' | 'DOWNLOAD' | 'CONVERSION' | 'MAPPING' | 'EXPORT';
export type PepCategoryKind = 'OFFICIAL' | 'AMLR' | 'RISK_BASED' | 'EU' | 'RCA';
export type CoverageStatus = 'COVERED' | 'PARTIAL' | 'NONE';

export interface Country {
  iso: string;
  name: string;
  euMember: boolean;
  legalBasis?: string | null;
  retentionMonths: number;
  officialListReference?: string | null;
  officialListDate?: string | null;
  officialListUrl?: string | null;
}

export interface Execution {
  id: number;
  sourceId: number;
  sourceName: string;
  sourceLabel?: string | null;
  status: ExecutionStatus;
  startedAt: string;
  finishedAt?: string | null;
  sourceFiles?: number | null;
  sourceRecords?: number | null;
  mappedRecords?: number | null;
  entityCount?: number | null;
  personCount?: number | null;
  entitiesBySchema?: string | null;
  outputPath?: string | null;
  failedStep?: FailedStep | null;
  errorMessage?: string | null;
  errorDetail?: string | null;
}

export interface CoveredFunction {
  functionId: number;
  categoryCode: string;
  label: string;
  completeness: Completeness;
  includesFormer: boolean;
}

export interface Source {
  id: number;
  sourceName: string;
  label?: string | null;
  description?: string | null;
  attribution?: string | null;
  sourceFormat: SourceFormat;
  parserType?: ParserType | null;
  listType?: ListType | null;
  countryIso?: string | null;
  externalSourcePath?: string | null;
  mappingFilePath?: string | null;
  ftmOutputPath?: string | null;
  converterOptions?: string | null;
  retentionMonths?: number | null;
  enabled: boolean;
  implemented: boolean;
  /** Source définie dans le catalogue : elle peut être réinitialisée. */
  inCatalog: boolean;
  /** Source modifiée dans l'interface : le catalogue ne l'écrase plus. */
  customized: boolean;
  coverage: CoveredFunction[];
  lastExecution?: Execution | null;
}

/** Corps de création / modification d'une source (POST et PUT /api/sources). */
export interface SourceRequest {
  sourceName: string;
  label: string;
  description?: string | null;
  attribution?: string | null;
  sourceFormat: SourceFormat | null;
  listType?: ListType | null;
  parserType?: ParserType | null;
  countryIso?: string | null;
  retentionMonths?: number | null;
  externalSourcePath: string;
  mappingFilePath?: string | null;
  converterOptions?: string | null;
  enabled?: boolean;
  coverage: { functionId: number; completeness: Completeness; includesFormer: boolean }[];
}

/** Fonction PEP proposée pour la couverture d'une source. */
export interface PepFunction {
  id: number;
  categoryCode: string;
  categoryLabel: string;
  categoryKind: PepCategoryKind;
  categoryOrder: number;
  label: string;
  institution?: string | null;
}

export interface CoverageSource {
  id: number;
  sourceName: string;
  label?: string | null;
  url?: string | null;
  completeness: Completeness;
  includesFormer: boolean;
  enabled: boolean;
  implemented: boolean;
  lastStatus?: ExecutionStatus | null;
  lastSuccessAt?: string | null;
  personCount?: number | null;
}

export interface CoverageFunction {
  id: number;
  label: string;
  institution?: string | null;
  expectedHolders?: number | null;
  status: CoverageStatus;
  sources: CoverageSource[];
}

export interface CoverageCategory {
  id: number;
  code: string;
  label: string;
  kind: PepCategoryKind;
  riskClass: number;
  legalReference?: string | null;
  status: CoverageStatus;
  functions: CoverageFunction[];
}

export interface Coverage {
  country: Country;
  officialCategories: number;
  coveredOfficialCategories: number;
  personCount: number;
  categories: CoverageCategory[];
}

/** Entité FollowTheMoney d'un fichier produit. */
export interface FtmEntity {
  id: string;
  schema: string;
  properties: Record<string, string[]>;
}

/** Corps d'une réponse d'erreur de l'API (ApiError). */
export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  path: string;
  fields?: Record<string, string>;
}
