/** Types échangés avec l'API REST du back (voir com.example.app.api). */

export type ListType = 'SANCTION' | 'PEP';
export type ParserType =
  | 'OPEN_DATA_FILE' | 'API' | 'SPARQL' | 'OFFICIAL_GAZETTE' | 'WEB_PAGE' | 'PDF_DOCUMENT' | 'DECLARATION_REGISTER';
export type SourceFormat = 'XML' | 'JSON' | 'CSV' | 'XLSX' | 'HTML' | 'PDF';
export type Completeness = 'COMPLETE' | 'PARTIAL';
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

export interface PepCategory {
  id: number;
  countryIso: string;
  code: string;
  label: string;
  kind: PepCategoryKind;
  riskClass: number;
  sortOrder: number;
  legalReference?: string | null;
  functionCount: number;
}

export interface PepFunction {
  id: number;
  categoryId: number;
  categoryCode: string;
  categoryLabel: string;
  categoryKind: PepCategoryKind;
  categoryOrder: number;
  countryIso: string;
  label: string;
  institution?: string | null;
  expectedHolders?: number | null;
  sourceCount: number;
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
  listType?: ListType | null;
  parserType?: ParserType | null;
  countryIso?: string | null;
  /** Fonction PEP principale (toujours présente dans la couverture). */
  pepFunctionId?: number | null;
  pepFunctionLabel?: string | null;
  pepFunctionCategoryCode?: string | null;
  retentionMonths?: number | null;
  enabled: boolean;
  externalSourcePath?: string | null;
  localSourcePath?: string | null;
  initialJsonSourcePath?: string | null;
  mappingFilePath?: string | null;
  ftmOutputPath?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  coverage: CoveredFunction[];
}

export interface CoverageItem {
  functionId: number;
  completeness: Completeness;
  includesFormer: boolean;
}

/** Corps de POST et PUT /api/sources. */
export interface SourceRequest {
  sourceName: string;
  label: string;
  description?: string | null;
  attribution?: string | null;
  sourceFormat: SourceFormat | null;
  listType?: ListType | null;
  parserType?: ParserType | null;
  countryIso?: string | null;
  pepFunctionId?: number | null;
  retentionMonths?: number | null;
  enabled: boolean;
  externalSourcePath: string;
  localSourcePath?: string | null;
  initialJsonSourcePath?: string | null;
  mappingFilePath?: string | null;
  ftmOutputPath?: string | null;
  coverage: CoverageItem[];
}

export interface CountryRequest {
  iso: string;
  name: string;
  euMember: boolean;
  legalBasis?: string | null;
  retentionMonths: number | null;
  officialListReference?: string | null;
  officialListDate?: string | null;
  officialListUrl?: string | null;
}

export interface CategoryRequest {
  countryIso: string;
  code: string;
  label: string;
  kind: PepCategoryKind | null;
  riskClass: number | null;
  sortOrder: number | null;
  legalReference?: string | null;
}

export interface FunctionRequest {
  categoryId: number | null;
  label: string;
  institution?: string | null;
  expectedHolders?: number | null;
}

export interface CoverageSource {
  id: number;
  sourceName: string;
  label?: string | null;
  completeness: Completeness;
  includesFormer: boolean;
  enabled: boolean;
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
  coveredFunctions: number;
  totalFunctions: number;
  categories: CoverageCategory[];
}

/** Corps d'une réponse d'erreur de l'API (ApiError). */
export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  path: string;
  fields?: Record<string, string>;
}
