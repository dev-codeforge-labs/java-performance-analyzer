import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  UploadResponse, AnalysisResultDto, RulesetDto, DiffResultDto
} from '../models/analysis.models';

@Injectable({ providedIn: 'root' })
export class ApiService {

  private readonly base = '/api/v1';

  constructor(private http: HttpClient) {}

  upload(files: File[]): Observable<UploadResponse> {
    const form = new FormData();
    files.forEach(f => form.append('files', f, f.name));
    return this.http.post<UploadResponse>(`${this.base}/upload`, form);
  }

  analyze(sessionId: string, ruleset: RulesetDto | null): Observable<AnalysisResultDto> {
    return this.http.post<AnalysisResultDto>(
      `${this.base}/analyze/${sessionId}`, ruleset ?? null);
  }

  getResult(sessionId: string): Observable<AnalysisResultDto> {
    return this.http.get<AnalysisResultDto>(`${this.base}/analyze/${sessionId}`);
  }

  diff(sessionIdA: string, sessionIdB: string, deltaThreshold = 5.0): Observable<DiffResultDto> {
    const params = new HttpParams()
      .set('sessionIdA', sessionIdA)
      .set('sessionIdB', sessionIdB)
      .set('deltaThreshold', deltaThreshold);
    return this.http.post<DiffResultDto>(`${this.base}/diff`, null, { params });
  }

  clearSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/session/${sessionId}`);
  }

  exportPdfUrl(sessionId: string): string {
    return `${this.base}/export/pdf/${sessionId}`;
  }

  exportJsonUrl(sessionId: string): string {
    return `${this.base}/export/json/${sessionId}`;
  }

  exportCsvUrl(sessionId: string): string {
    return `${this.base}/export/csv/${sessionId}/hotspots`;
  }

  exportDocxUrl(sessionId: string): string {
    return `${this.base}/export/docx/${sessionId}`;
  }
}
