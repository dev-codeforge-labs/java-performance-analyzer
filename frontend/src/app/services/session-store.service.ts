import { Injectable, signal } from '@angular/core';
import { AnalysisResultDto, UploadResponse } from '../models/analysis.models';

export interface SessionEntry {
  uploadResponse: UploadResponse;
  result: AnalysisResultDto | null;
  label: 'A' | 'B' | null;
}

@Injectable({ providedIn: 'root' })
export class SessionStoreService {
  sessions = signal<SessionEntry[]>([]);
  activeSessionId = signal<string | null>(null);
  slotA = signal<string | null>(null);
  slotB = signal<string | null>(null);

  addSession(upload: UploadResponse): void {
    const entry: SessionEntry = { uploadResponse: upload, result: null, label: null };
    this.sessions.update(s => [entry, ...s]);
    this.activeSessionId.set(upload.sessionId);
  }

  setResult(sessionId: string, result: AnalysisResultDto): void {
    this.sessions.update(sessions =>
      sessions.map(s =>
        s.uploadResponse.sessionId === sessionId ? { ...s, result } : s
      )
    );
  }

  getEntry(sessionId: string): SessionEntry | undefined {
    return this.sessions().find(s => s.uploadResponse.sessionId === sessionId);
  }

  setSlot(sessionId: string, slot: 'A' | 'B'): void {
    if (slot === 'A') this.slotA.set(sessionId);
    else this.slotB.set(sessionId);
    this.sessions.update(sessions =>
      sessions.map(s => ({
        ...s,
        label: s.uploadResponse.sessionId === sessionId ? slot
          : s.label === slot ? null : s.label
      }))
    );
  }

  getActiveResult(): AnalysisResultDto | null {
    const id = this.activeSessionId();
    return id ? (this.getEntry(id)?.result ?? null) : null;
  }
}
