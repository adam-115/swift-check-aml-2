import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CategoryRequest, Country, CountryRequest, Coverage, FunctionRequest, ListType, PepCategory, PepFunction, Source,
  SourceRequest,
} from '../models/api';

/** Accès à l'API REST du back (/api). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Sources
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

  createSource(request: SourceRequest): Observable<Source> {
    return this.http.post<Source>('/api/sources', request);
  }

  updateSource(id: number, request: SourceRequest): Observable<Source> {
    return this.http.put<Source>(`/api/sources/${id}`, request);
  }

  setSourceEnabled(id: number, enabled: boolean): Observable<Source> {
    return this.http.patch<Source>(`/api/sources/${id}/enabled`, { enabled });
  }

  deleteSource(id: number): Observable<void> {
    return this.http.delete<void>(`/api/sources/${id}`);
  }

  // Pays
  countries(): Observable<Country[]> {
    return this.http.get<Country[]>('/api/countries');
  }

  createCountry(request: CountryRequest): Observable<Country> {
    return this.http.post<Country>('/api/countries', request);
  }

  updateCountry(iso: string, request: CountryRequest): Observable<Country> {
    return this.http.put<Country>(`/api/countries/${iso}`, request);
  }

  deleteCountry(iso: string): Observable<void> {
    return this.http.delete<void>(`/api/countries/${iso}`);
  }

  // Catégories et fonctions PEP
  categories(countryIso: string): Observable<PepCategory[]> {
    return this.http.get<PepCategory[]>('/api/pep/categories', { params: { country: countryIso } });
  }

  createCategory(request: CategoryRequest): Observable<PepCategory> {
    return this.http.post<PepCategory>('/api/pep/categories', request);
  }

  updateCategory(id: number, request: CategoryRequest): Observable<PepCategory> {
    return this.http.put<PepCategory>(`/api/pep/categories/${id}`, request);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`/api/pep/categories/${id}`);
  }

  functions(countryIso: string): Observable<PepFunction[]> {
    return this.http.get<PepFunction[]>('/api/pep/functions', { params: { country: countryIso } });
  }

  createFunction(request: FunctionRequest): Observable<PepFunction> {
    return this.http.post<PepFunction>('/api/pep/functions', request);
  }

  updateFunction(id: number, request: FunctionRequest): Observable<PepFunction> {
    return this.http.put<PepFunction>(`/api/pep/functions/${id}`, request);
  }

  deleteFunction(id: number): Observable<void> {
    return this.http.delete<void>(`/api/pep/functions/${id}`);
  }

  coverage(countryIso: string): Observable<Coverage> {
    return this.http.get<Coverage>('/api/pep/coverage', { params: { country: countryIso } });
  }
}
