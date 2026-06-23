import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { RulesetService } from '../../services/ruleset.service';
import { SessionStoreService } from '../../services/session-store.service';
import { I18nService } from '../../services/i18n.service';
import { AnalysisResultDto, UploadResponse } from '../../models/analysis.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
<div class="max-w-6xl mx-auto space-y-6">
  <h1 class="text-2xl font-bold">Dashboard</h1>

  <!-- Upload Zone -->
  <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-8"
       [class.border-blue-400]="dragging()"
       (dragover)="$event.preventDefault(); dragging.set(true)"
       (dragleave)="dragging.set(false)"
       (drop)="onDrop($event)">
    <div class="text-center space-y-3">
      <div class="text-4xl">📂</div>
      <p class="text-gray-600 dark:text-gray-400">Drag &amp; drop JVM dump files here, or</p>
      <label class="cursor-pointer bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 text-sm inline-block">
        Choose Files
        <input type="file" multiple class="hidden" (change)="onFileInput($event)"
               accept=".txt,.log,.hprof,.jfr">
      </label>
      <p class="text-xs text-gray-400">Supported: .txt, .log (thread dump / GC log), .hprof, .jfr · Max 500 MB</p>
    </div>
  </div>

  <!-- Warnings -->
  @if (warnings().length > 0) {
    <div class="space-y-1">
      @for (w of warnings(); track w) {
        <div class="bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-700 rounded px-4 py-2 text-sm text-amber-800 dark:text-amber-200 flex justify-between">
          <span>⚠ {{ w }}</span>
          <button (click)="dismissWarning(w)" class="text-amber-500 hover:text-amber-700 ml-4">✕</button>
        </div>
      }
    </div>
  }

  <!-- Loading -->
  @if (loading()) {
    <div class="text-center py-8 text-gray-500">
      <div class="animate-pulse text-lg">Analyzing...</div>
    </div>
  }

  <!-- Results -->
  @if (result(); as r) {
    <!-- Health Score -->
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-6 flex items-center gap-6">
      @if (r.healthScore) {
        <div class="flex flex-col items-center">
          <div class="text-5xl font-bold" [class]="healthColor(r.healthScore.classification)">
            {{ r.healthScore.score }}
          </div>
          <div class="text-xs text-gray-400 mt-1">Health Score</div>
          <div class="mt-1 px-2 py-0.5 rounded text-xs font-semibold" [class]="healthBadge(r.healthScore.classification)">
            {{ r.healthScore.classification }}
          </div>
        </div>
        <div class="flex-1">
          <p class="text-sm text-gray-600 dark:text-gray-400">{{ r.healthScore.dominantFactor }}</p>
          <div class="mt-3 grid grid-cols-2 gap-2">
            @for (b of r.healthScore.signalBreakdown; track b.signal) {
              <div class="text-xs">
                <span class="text-gray-500">{{ b.signal }}</span>:
                <span class="font-medium">{{ b.rawValue | number:'1.0-1' }}</span>
              </div>
            }
          </div>
        </div>
      }
    </div>

    <!-- Stat Cards -->
    <div class="grid grid-cols-4 gap-4">
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold">{{ r.totalThreads }}</div>
        <div class="text-sm text-gray-500">Total Threads</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold text-red-600">
          {{ blockedPct(r) | number:'1.0-1' }}%
        </div>
        <div class="text-sm text-gray-500">Blocked</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold text-green-600">
          {{ runnablePct(r) | number:'1.0-1' }}%
        </div>
        <div class="text-sm text-gray-500">Runnable</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold">{{ r.snapshotCount }}</div>
        <div class="text-sm text-gray-500">Snapshots</div>
      </div>
    </div>

    <!-- Critical Diagnoses -->
    @if (criticals(r).length > 0) {
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-red-300 dark:border-red-700 p-4">
        <h3 class="font-semibold text-red-600 dark:text-red-400 mb-2">Critical Issues</h3>
        <ul class="space-y-1">
          @for (d of criticals(r); track d.ruleId) {
            <li class="text-sm text-red-700 dark:text-red-300">• {{ d.message }}</li>
          }
        </ul>
      </div>
    }

    <!-- Export buttons -->
    <div class="flex flex-wrap gap-2 items-center">
      <span class="text-xs text-gray-400 mr-1">{{ i18n.t('export.download') }}:</span>
      <a [href]="api.exportPdfUrl(r.sessionId)" target="_blank"
         class="text-sm px-3 py-1.5 rounded bg-red-600 text-white hover:bg-red-700 font-medium">⬇ {{ i18n.t('export.pdf') }}</a>
      <a [href]="api.exportDocxUrl(r.sessionId)" target="_blank"
         class="text-sm px-3 py-1.5 rounded bg-blue-700 text-white hover:bg-blue-800 font-medium">⬇ {{ i18n.t('export.docx') }}</a>
      <a [href]="api.exportJsonUrl(r.sessionId)" target="_blank"
         class="text-sm px-3 py-1.5 rounded bg-gray-700 text-white hover:bg-gray-800 font-medium">⬇ {{ i18n.t('export.json') }}</a>
      <a [href]="api.exportCsvUrl(r.sessionId)" target="_blank"
         class="text-sm px-3 py-1.5 rounded bg-green-700 text-white hover:bg-green-800 font-medium">⬇ {{ i18n.t('export.csv') }}</a>
      <a routerLink="/hotspots" class="ml-2 text-sm px-4 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700">{{ i18n.t('dashboard.viewHotspots') }}</a>
    </div>
  }

  <!-- Session list -->
  @if (store.sessions().length > 0) {
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <h3 class="font-semibold mb-2 text-sm">Recent Sessions</h3>
      <ul class="space-y-1">
        @for (s of store.sessions(); track s.uploadResponse.sessionId) {
          <li class="flex items-center gap-3 text-sm">
            <button (click)="switchSession(s.uploadResponse.sessionId)"
                    class="text-blue-600 hover:underline">
              {{ s.uploadResponse.fileNames.join(', ') }}
            </button>
            <span class="text-gray-400 text-xs">{{ s.uploadResponse.detectedTypes.join(' + ') }}</span>
            @if (s.label) { <span class="text-xs bg-blue-100 dark:bg-blue-900 px-1 rounded">Slot {{ s.label }}</span> }
          </li>
        }
      </ul>
    </div>
  }
</div>
  `,
})
export class DashboardComponent {
  dragging = signal(false);
  loading = signal(false);
  warnings = signal<string[]>([]);
  result = signal<AnalysisResultDto | null>(null);

  constructor(
    readonly api: ApiService,
    private ruleset: RulesetService,
    readonly store: SessionStoreService,
    readonly i18n: I18nService,
  ) {
    // Restore last active result if any
    const active = store.getActiveResult();
    if (active) this.result.set(active);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (files.length) this.upload(files);
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (files.length) this.upload(files);
  }

  private upload(files: File[]): void {
    const oversized = files.filter(f => f.size > 524_288_000);
    if (oversized.length) {
      this.warnings.update(w => [...w, `File(s) exceed 500 MB limit: ${oversized.map(f => f.name).join(', ')}`]);
      return;
    }
    this.loading.set(true);
    this.api.upload(files).subscribe({
      next: (upload) => {
        this.store.addSession(upload);
        if (upload.warnings.length) {
          this.warnings.update(w => [...w, ...upload.warnings]);
        }
        this.api.analyze(upload.sessionId, this.ruleset.ruleset()).subscribe({
          next: (r) => {
            this.store.setResult(upload.sessionId, r);
            this.result.set(r);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  switchSession(id: string): void {
    this.store.activeSessionId.set(id);
    const entry = this.store.getEntry(id);
    if (entry?.result) {
      this.result.set(entry.result);
    }
  }

  dismissWarning(w: string): void {
    this.warnings.update(ws => ws.filter(x => x !== w));
  }

  healthColor(cls: string): string {
    return cls === 'GREEN' ? 'text-green-600 dark:text-green-400'
         : cls === 'YELLOW' ? 'text-amber-500 dark:text-amber-400'
         : 'text-red-600 dark:text-red-400';
  }

  healthBadge(cls: string): string {
    return cls === 'GREEN' ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
         : cls === 'YELLOW' ? 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'
         : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
  }

  blockedPct(r: AnalysisResultDto): number {
    if (!r.timeline?.snapshots?.length) return 0;
    const last = r.timeline.snapshots[r.timeline.snapshots.length - 1];
    return last.blockedPercent;
  }

  runnablePct(r: AnalysisResultDto): number {
    if (!r.timeline?.snapshots?.length) return 0;
    const last = r.timeline.snapshots[r.timeline.snapshots.length - 1];
    return last.runnablePercent;
  }

  criticals(r: AnalysisResultDto) {
    return (r.diagnoses ?? []).filter(d => d.severity === 'CRITICAL');
  }
}
