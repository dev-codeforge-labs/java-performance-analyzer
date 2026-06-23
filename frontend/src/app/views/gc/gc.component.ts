import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionStoreService } from '../../services/session-store.service';
import { ApiService } from '../../services/api.service';
import { I18nService } from '../../services/i18n.service';

@Component({
  selector: 'app-gc',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="max-w-6xl mx-auto space-y-4">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('gc.title') }}</h1>
    @if (sessionId()) {
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400">{{ i18n.t('export.download') }}:</span>
        <a [href]="api.exportPdfUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-red-600 text-white hover:bg-red-700">⬇ {{ i18n.t('export.pdf') }}</a>
        <a [href]="api.exportDocxUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-blue-700 text-white hover:bg-blue-800">⬇ {{ i18n.t('export.docx') }}</a>
      </div>
    }
  </div>

  @if (!gc()) {
    <p class="text-gray-500">{{ i18n.t('gc.noData') }}</p>
  } @else {
    <!-- Summary cards -->
    <div class="grid grid-cols-4 gap-4">
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold">{{ gc()!.gcAlgorithm }}</div>
        <div class="text-sm text-gray-500">GC Algorithm</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold" [class]="thrashColor()">{{ gc()!.gcTimePercent | number:'1.1-1' }}%</div>
        <div class="text-sm text-gray-500">GC Time %</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold">{{ gc()!.maxPauseMs | number:'1.0-0' }} ms</div>
        <div class="text-sm text-gray-500">Max Pause</div>
      </div>
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div class="text-2xl font-bold">{{ gc()!.p99PauseMs | number:'1.0-0' }} ms</div>
        <div class="text-sm text-gray-500">p99 Pause</div>
      </div>
    </div>

    <!-- Alerts -->
    @if (gc()!.promotionFailureDetected) {
      <div class="bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 rounded p-3 text-sm text-red-700 dark:text-red-300">
        ⚠ <strong>Promotion Failure detected</strong> — Old Gen filling faster than GC can collect. Risk of OutOfMemoryError.
      </div>
    }
    @if (gc()!.concurrentModeFailureDetected) {
      <div class="bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 rounded p-3 text-sm text-red-700 dark:text-red-300">
        ⚠ <strong>Concurrent Mode Failure detected</strong> — CMS/G1 falling back to full STW collection.
      </div>
    }
    @if (gc()!.gcTimePercent > 10) {
      <div class="bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-700 rounded p-3 text-sm text-amber-700 dark:text-amber-300">
        ⚠ GC consuming {{ gc()!.gcTimePercent | number:'1.1-1' }}% of elapsed time — consider increasing heap size.
      </div>
    }

    <!-- Algorithm recommendation -->
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 text-sm">
      <h3 class="font-semibold mb-1">Algorithm Recommendation</h3>
      <p class="text-gray-600 dark:text-gray-400">{{ gcRecommendation() }}</p>
    </div>

    <!-- STW Event table -->
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div class="px-4 py-2 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
        <h3 class="font-semibold text-sm">STW Events ({{ gc()!.events.length }} total)</h3>
        <span class="text-xs text-gray-400">Avg: {{ gc()!.avgPauseMs | number:'1.1-1' }} ms</span>
      </div>
      <div class="overflow-y-auto max-h-96">
        <table class="w-full text-xs">
          <thead class="bg-gray-50 dark:bg-gray-800 text-gray-400 sticky top-0">
            <tr>
              <th class="px-3 py-2 text-left">Time (s)</th>
              <th class="px-3 py-2 text-left">Type</th>
              <th class="px-3 py-2 text-right">Pause (ms)</th>
              <th class="px-3 py-2 text-right">Heap Before</th>
              <th class="px-3 py-2 text-right">Heap After</th>
            </tr>
          </thead>
          <tbody>
            @for (ev of gc()!.events; track $index) {
              <tr class="border-t border-gray-100 dark:border-gray-800">
                <td class="px-3 py-1.5">{{ ev.timestamp | number:'1.3-3' }}</td>
                <td class="px-3 py-1.5">{{ ev.type }}</td>
                <td class="px-3 py-1.5 text-right font-medium" [class]="pauseColor(ev.pauseMs)">
                  {{ ev.pauseMs | number:'1.1-1' }}
                </td>
                <td class="px-3 py-1.5 text-right text-gray-500">{{ mb(ev.heapBeforeBytes) }} MB</td>
                <td class="px-3 py-1.5 text-right text-gray-500">{{ mb(ev.heapAfterBytes) }} MB</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  }
</div>
  `,
})
export class GcComponent {
  private r = computed(() => this.store.getActiveResult());
  gc = computed(() => this.r()?.gcSummary ?? null);
  sessionId = computed(() => this.r()?.sessionId ?? null);

  constructor(readonly store: SessionStoreService, readonly api: ApiService, readonly i18n: I18nService) {}

  thrashColor(): string {
    const pct = this.gc()?.gcTimePercent ?? 0;
    return pct >= 30 ? 'text-red-600 dark:text-red-400' : pct >= 10 ? 'text-amber-500' : 'text-green-600';
  }

  pauseColor(ms: number): string {
    return ms >= 500 ? 'text-red-600 dark:text-red-400' : ms >= 100 ? 'text-amber-500' : 'text-gray-700 dark:text-gray-300';
  }

  mb(bytes: number): string { return (bytes / 1_048_576).toFixed(0); }

  gcRecommendation(): string {
    const algo = this.gc()?.gcAlgorithm ?? '';
    const map: Record<string, string> = {
      'PARALLEL': 'You are using Parallel GC. For lower pause times with large heaps, consider migrating to G1 (-XX:+UseG1GC).',
      'SERIAL': 'Serial GC is single-threaded. Recommended only for small heaps (<256 MB) or embedded environments.',
      'G1': 'G1 GC is a good general-purpose choice. Tune -XX:MaxGCPauseMillis to your SLA target.',
      'ZGC': 'ZGC provides sub-millisecond pauses and scales well. Ensure heap is >= 1 GB for best performance.',
      'SHENANDOAH': 'Shenandoah provides very low pause times. Works best with moderate heap sizes.',
      'CMS': 'CMS is deprecated since JDK 9. Migrate to G1 or ZGC.',
    };
    return map[algo] ?? `GC algorithm: ${algo || 'unknown'}. No specific recommendation available.`;
  }
}
