import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Country, Coverage, Execution, FtmEntity, ListType, PepFunction, Source, SourceRequest } from '../models/api';

/** Accès à l'API REST du back (/api). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  countries(): Observable<Country[]> {
    return this.http.get<Country[]>('/api/countries');
  }

  coverage(countryIso: string): Observable<Coverage> {
    return this.http.get<Coverage>('/api/pep/coverage', { params: { country: countryIso } });
  }

  sources(filter: { listType?: ListType | ''; country?: string } = {}): Observable<Source[]> {
    let params = new HttpParams();
    if (filter.listType) {
      params = params.set('listType', filter.listType);
    }
    if (filter.country) {
      params = params.set('country', filter.country);
    }
    return this.http.get<Source[]>('/api/sources', { params });
  }

  updateSource(id: number, changes: { enabled?: boolean; externalSourcePath?: string }): Observable<Source> {
    return this.http.patch<Source>(`/api/sources/${id}`, changes);
  }

  createSource(request: SourceRequest): Observable<Source> {
    return this.http.post<Source>('/api/sources', request);
  }

  replaceSource(id: number, request: SourceRequest): Observable<Source> {
    return this.http.put<Source>(`/api/sources/${id}`, request);
  }

  deleteSource(id: number): Observable<void> {
    return this.http.delete<void>(`/api/sources/${id}`);
  }

  resetSource(id: number): Observable<Source> {
    return this.http.post<Source>(`/api/sources/${id}/reset`, null);
  }

  pepFunctions(countryIso: string): Observable<PepFunction[]> {
    return this.http.get<PepFunction[]>('/api/pep/functions', { params: { country: countryIso } });
  }

  runSource(id: number): Observable<Execution> {
    return this.http.post<Execution>(`/api/sources/${id}/executions`, null);
  }

  executions(sourceId?: number | null, limit = 100): Observable<Execution[]> {
    let params = new HttpParams().set('limit', limit);
    if (sourceId) {
      params = params.set('sourceId', sourceId);
    }
    return this.http.get<Execution[]>('/api/executions', { params });
  }

  execution(id: number): Observable<Execution> {
    return this.http.get<Execution>(`/api/executions/${id}`);
  }

  executionEntities(id: number, limit = 300): Observable<FtmEntity[]> {
    return this.http.get<FtmEntity[]>(`/api/executions/${id}/entities`, { params: { limit } });
  }
}
