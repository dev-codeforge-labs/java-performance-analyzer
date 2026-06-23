import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { SessionStoreService } from '../../services/session-store.service';
import { I18nService } from '../../services/i18n.service';
import { DiffResultDto } from '../../models/analysis.models';

@Component({
  selector: 'app-diff',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="max-w-6xl mx-auto space-y-4">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('diff.title') }}</h1>
    @if (diffResult()) {
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400">{{ i18n.t('export.download') }}:</span>
        <a [href]="api.exportPdfUrl(store.slotA()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-red-600 text-white hover:bg-red-700">⬇ {{ i18n.t('export.pdf') }}</a>
        <a [href]="api.exportDocxUrl(store.slotA()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-blue-700 text-white hover:bg-blue-800">⬇ {{ i18n.t('export.docx') }}</a>
      </div>
    }
  </div>

  <!-- Slot selector -->
  <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 grid grid-cols-2 gap-4">
    <div>
      <h3 class="font-semibold mb-2 text-sm">Analysis A (Baseline)</h3>
      @for (s of store.sessions(); track s.uploadResponse.sessionId) {
        <label class="flex items-center gap-2 text-sm cursor-pointer py-0.5">
          <input type="radio" name="slotA" [value]="s.uploadResponse.sessionId"
                 [checked]="store.slotA() === s.uploadResponse.sessionId"
                 (change)="store.setSlot(s.uploadResponse.sessionId, 'A')">
          {{ s.uploadResponse.fileNames.join(', ') }}
          @if (!s.result) { <span class="text-xs text-red-400">(not analyzed)</span> }
        </label>
      }
      @if (!store.sessions().length) { <p class="text-gray-400 text-sm">No sessions yet.</p> }
    </div>
    <div>
      <h3 class="font-semibold mb-2 text-sm">Analysis B (Comparison)</h3>
      @for (s of store.sessions(); track s.uploadResponse.sessionId) {
        <label class="flex items-center gap-2 text-sm cursor-pointer py-0.5">
          <input type="radio" name="slotB" [value]="s.uploadResponse.sessionId"
                 [checked]="store.slotB() === s.uploadResponse.sessionId"
                 (change)="store.setSlot(s.uploadResponse.sessionId, 'B')">
          {{ s.uploadResponse.fileNames.join(', ') }}
        </label>
      }
    </div>
  </div>

  <button (click)="runDiff()" [disabled]="!canDiff() || loading()"
          class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-40 text-sm">
    {{ loading() ? 'Computing diff…' : 'Compare A vs B' }}
  </button>

  @if (diffResult(); as diff) {
    <!-- Health Score comparison -->
    @if (diff.healthScoreA && diff.healthScoreB) {
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 text-center">
          <div class="text-xs text-gray-400 mb-1">Analysis A — Health Score</div>
          <div class="text-4xl font-bold" [class]="scoreColor(diff.healthScoreA.classification)">
            {{ diff.healthScoreA.score }}
          </div>
          <div class="text-xs mt-1">{{ diff.healthScoreA.classification }}</div>
        </div>
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 text-center">
          <div class="text-xs text-gray-400 mb-1">Analysis B — Health Score</div>
          <div class="text-4xl font-bold" [class]="scoreColor(diff.healthScoreB.classification)">
            {{ diff.healthScoreB.score }}
          </div>
          <div class="text-xs mt-1">
            {{ diff.healthScoreB.classification }}
            <span class="ml-1 font-bold" [class]="deltaClass(diff.healthScoreB.score - diff.healthScoreA.score)">
              {{ delta(diff.healthScoreB.score - diff.healthScoreA.score) }}
            </span>
          </div>
        </div>
      </div>
    }

    <!-- Tabs -->
    <div class="flex gap-2 text-sm border-b border-gray-200 dark:border-gray-700">
      @for (tab of tabs; track tab) {
        <button (click)="activeTab.set(tab)"
                class="px-4 py-2 -mb-px border-b-2"
                [class]="activeTab() === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'">
          {{ tab }}
        </button>
      }
    </div>

    <!-- Hotspot Diff -->
    @if (activeTab() === 'Hotspots') {
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
        <table class="w-full text-xs">
          <thead class="bg-gray-50 dark:bg-gray-800 text-gray-400">
            <tr>
              <th class="px-3 py-2 text-left">Method</th>
              <th class="px-3 py-2 text-center">Rank A</th>
              <th class="px-3 py-2 text-center">Rank B</th>
              <th class="px-3 py-2 text-right">Self% A</th>
              <th class="px-3 py-2 text-right">Self% B</th>
              <th class="px-3 py-2 text-right">Delta</th>
              <th class="px-3 py-2 text-center">Trend</th>
            </tr>
          </thead>
          <tbody>
            @for (h of diff.hotspotDiff; track h.methodSignature) {
              <tr class="border-t border-gray-100 dark:border-gray-800">
                <td class="px-3 py-1.5 font-mono truncate max-w-xs" [title]="h.methodSignature">{{ shortSig(h.methodSignature) }}</td>
                <td class="px-3 py-1.5 text-center text-gray-500">{{ h.rankA ?? '-' }}</td>
                <td class="px-3 py-1.5 text-center text-gray-500">{{ h.rankB ?? '-' }}</td>
                <td class="px-3 py-1.5 text-right">{{ h.selfTimePctA | number:'1.1-1' }}%</td>
                <td class="px-3 py-1.5 text-right">{{ h.selfTimePctB | number:'1.1-1' }}%</td>
                <td class="px-3 py-1.5 text-right font-medium" [class]="deltaClass(h.delta)">{{ delta(h.delta) }}</td>
                <td class="px-3 py-1.5 text-center">
                  <span class="px-1.5 py-0.5 rounded text-xs" [class]="trendBadge(h.trend)">{{ h.trend }}</span>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    <!-- Diagnostic Diff -->
    @if (activeTab() === 'Diagnostics') {
      <div class="grid grid-cols-3 gap-4">
        <div class="bg-green-50 dark:bg-green-900/20 rounded-lg border border-green-200 dark:border-green-700 p-3">
          <h3 class="font-semibold text-green-700 dark:text-green-300 mb-2 text-sm">✓ Resolved ({{ diff.diagnosticDiff.resolved.length }})</h3>
          @for (d of diff.diagnosticDiff.resolved; track d.ruleId) {
            <p class="text-xs text-green-700 dark:text-green-300 mb-1">{{ d.message }}</p>
          }
        </div>
        <div class="bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-700 p-3">
          <h3 class="font-semibold text-red-700 dark:text-red-300 mb-2 text-sm">✗ New ({{ diff.diagnosticDiff.added.length }})</h3>
          @for (d of diff.diagnosticDiff.added; track d.ruleId) {
            <p class="text-xs text-red-700 dark:text-red-300 mb-1">{{ d.message }}</p>
          }
        </div>
        <div class="bg-gray-50 dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-3">
          <h3 class="font-semibold text-gray-600 dark:text-gray-300 mb-2 text-sm">↔ Persisting ({{ diff.diagnosticDiff.persisting.length }})</h3>
          @for (d of diff.diagnosticDiff.persisting; track d.ruleId) {
            <p class="text-xs text-gray-600 dark:text-gray-400 mb-1">{{ d.message }}</p>
          }
        </div>
      </div>
    }

    <!-- Thread State Diff -->
    @if (activeTab() === 'Thread States') {
      <div class="grid grid-cols-2 gap-4">
        @for (slot of ['A', 'B']; track slot) {
          <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
            <h3 class="font-semibold mb-3 text-sm">Analysis {{ slot }}</h3>
            @for (state of threadStates; track state) {
              <div class="flex items-center gap-2 mb-1 text-sm">
                <span class="w-28 text-gray-500 text-xs">{{ state }}</span>
                <div class="flex-1 bg-gray-100 dark:bg-gray-700 rounded h-3">
                  <div class="h-3 rounded" [class]="stateBarColor(state)"
                       [style.width.%]="getStatePct(slot, state)"></div>
                </div>
                <span class="text-xs w-10 text-right">{{ getStatePct(slot, state) | number:'1.0-0' }}%</span>
              </div>
            }
          </div>
        }
      </div>
    }
  }
</div>
  `,
})
export class DiffComponent {
  tabs = ['Hotspots', 'Diagnostics', 'Thread States'];
  activeTab = signal('Hotspots');
  loading = signal(false);
  diffResult = signal<DiffResultDto | null>(null);
  threadStates = ['RUNNABLE', 'BLOCKED', 'WAITING', 'TIMED_WAITING'];

  canDiff = computed(() => {
    const a = this.store.slotA();
    const b = this.store.slotB();
    return a && b && a !== b &&
      this.store.getEntry(a)?.result != null &&
      this.store.getEntry(b)?.result != null;
  });

  constructor(readonly store: SessionStoreService, readonly api: ApiService, readonly i18n: I18nService) {}

  runDiff(): void {
    const a = this.store.slotA()!;
    const b = this.store.slotB()!;
    this.loading.set(true);
    this.api.diff(a, b).subscribe({
      next: r => { this.diffResult.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  shortSig(sig: string): string {
    const p = sig.split('.'); return p.length > 3 ? '…' + p.slice(-3).join('.') : sig;
  }

  delta(d: number): string { return (d > 0 ? '+' : '') + d.toFixed(1); }

  deltaClass(d: number): string {
    return d > 1 ? 'text-red-600 dark:text-red-400' : d < -1 ? 'text-green-600 dark:text-green-400' : 'text-gray-500';
  }

  scoreColor(cls: string): string {
    return cls === 'GREEN' ? 'text-green-600' : cls === 'YELLOW' ? 'text-amber-500' : 'text-red-600';
  }

  trendBadge(trend: string): string {
    return trend === 'NEW' ? 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300'
         : trend === 'RESOLVED' ? 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300'
         : trend === 'UP' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300'
         : trend === 'DOWN' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
         : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400';
  }

  getStatePct(slot: string, state: string): number {
    const diff = this.diffResult();
    if (!diff) return 0;
    const map = slot === 'A' ? diff.threadStateDiffA : diff.threadStateDiffB;
    return map[state] ?? 0;
  }

  stateBarColor(state: string): string {
    return state === 'RUNNABLE' ? 'bg-green-500' : state === 'BLOCKED' ? 'bg-red-500'
         : state === 'WAITING' ? 'bg-blue-500' : 'bg-purple-500';
  }
}
