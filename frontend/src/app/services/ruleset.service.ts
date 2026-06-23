import { Injectable, signal } from '@angular/core';
import { RulesetDto, DEFAULT_RULESET } from '../models/analysis.models';

@Injectable({ providedIn: 'root' })
export class RulesetService {
  private readonly STORAGE_KEY = 'pa.ruleset';

  ruleset = signal<RulesetDto>(this.load());

  private load(): RulesetDto {
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (raw) return JSON.parse(raw) as RulesetDto;
    } catch { /* ignore */ }
    return { ...DEFAULT_RULESET };
  }

  save(ruleset: RulesetDto): void {
    this.ruleset.set(ruleset);
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(ruleset));
  }

  reset(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    this.ruleset.set({ ...DEFAULT_RULESET });
  }

  exportJson(): void {
    const data = { ...this.ruleset(), exportedAt: new Date().toISOString() };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'ruleset.json';
    a.click();
    URL.revokeObjectURL(url);
  }

  importFromFile(file: File): Promise<void> {
    return file.text().then(text => {
      const parsed = JSON.parse(text) as RulesetDto;
      this.save(parsed);
    });
  }
}
