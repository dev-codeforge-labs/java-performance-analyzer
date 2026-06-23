import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionStoreService } from '../../services/session-store.service';
import { ApiService } from '../../services/api.service';
import { I18nService } from '../../services/i18n.service';
import { HotspotDto, Diagnosis } from '../../models/analysis.models';

@Component({
  selector: 'app-hotspots',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="max-w-6xl mx-auto space-y-4">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('hotspots.title') }}</h1>
    <div class="flex items-center gap-3">
      @if (confirmedDeadlock()) {
        <span class="bg-red-600 text-white text-xs px-3 py-1 rounded-full font-semibold animate-pulse">
          ⚠ Confirmed Deadlock
        </span>
      }
      @if (sessionId()) {
        <span class="text-xs text-gray-400">{{ i18n.t('export.download') }}:</span>
        <a [href]="api.exportPdfUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1 rounded bg-red-600 text-white hover:bg-red-700">⬇ {{ i18n.t('export.pdf') }}</a>
        <a [href]="api.exportDocxUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1 rounded bg-blue-700 text-white hover:bg-blue-800">⬇ {{ i18n.t('export.docx') }}</a>
        <a [href]="api.exportCsvUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1 rounded bg-green-700 text-white hover:bg-green-800">⬇ {{ i18n.t('export.csv') }}</a>
      }
    </div>
  </div>

  @if (!hotspots().length) {
    <p class="text-gray-500">{{ i18n.t('hotspots.noData') }}</p>
  } @else {
    <div class="flex gap-6">
      <!-- Hotspot table -->
      <div class="flex-1 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
        <table class="w-full text-sm">
          <thead class="bg-gray-50 dark:bg-gray-800 text-gray-500 dark:text-gray-400 text-xs uppercase">
            <tr>
              <th class="px-3 py-2 text-left w-8">#</th>
              <th class="px-3 py-2 text-left cursor-pointer hover:text-gray-800" (click)="sort('methodSignature')">Method</th>
              <th class="px-3 py-2 text-left">Layer</th>
              <th class="px-3 py-2 text-right cursor-pointer hover:text-gray-800" (click)="sort('selfTimePercent')">Self%</th>
              <th class="px-3 py-2 text-right cursor-pointer hover:text-gray-800" (click)="sort('totalTimePercent')">Total%</th>
              <th class="px-3 py-2 text-center">Severity</th>
            </tr>
          </thead>
          <tbody>
            @for (h of sorted(); track h.rank) {
              <tr class="border-t border-gray-100 dark:border-gray-800 hover:bg-blue-50 dark:hover:bg-blue-900/20 cursor-pointer"
                  [class.bg-blue-50]="selected()?.rank === h.rank"
                  (click)="select(h)">
                <td class="px-3 py-2 text-gray-400">{{ h.rank }}</td>
                <td class="px-3 py-2 font-mono text-xs truncate max-w-xs" [title]="h.methodSignature">
                  {{ shortSig(h.methodSignature) }}
                </td>
                <td class="px-3 py-2">
                  <span class="text-xs px-1.5 py-0.5 rounded" [class]="layerColor(h.layerCategory)">
                    {{ h.layerCategory }}
                  </span>
                </td>
                <td class="px-3 py-2 text-right font-medium" [class]="heatColor(h.selfTimePercent)">
                  {{ h.selfTimePercent | number:'1.1-1' }}%
                </td>
                <td class="px-3 py-2 text-right text-gray-500">
                  {{ h.totalTimePercent | number:'1.1-1' }}%
                </td>
                <td class="px-3 py-2 text-center">
                  @if (h.severity) {
                    <span class="text-xs px-1.5 py-0.5 rounded" [class]="severityBadge(h.severity)">
                      {{ h.severity }}
                    </span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Inspector Drawer -->
      @if (selected(); as h) {
        <div class="w-80 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 space-y-3 text-sm">
          <div class="flex items-center justify-between">
            <h3 class="font-semibold">Solution Inspector</h3>
            <button (click)="selected.set(null)" class="text-gray-400 hover:text-gray-600">✕</button>
          </div>
          <div>
            <span class="text-gray-400 text-xs block">Method</span>
            <span class="font-mono text-xs break-all">{{ h.methodSignature }}</span>
          </div>
          <div>
            <span class="text-gray-400 text-xs block">Layer</span>
            <span class="px-1.5 py-0.5 rounded text-xs" [class]="layerColor(h.layerCategory)">{{ h.layerCategory }}</span>
          </div>
          <div class="flex gap-4">
            <div>
              <span class="text-gray-400 text-xs block">Self Time</span>
              <span class="font-bold text-lg" [class]="heatColor(h.selfTimePercent)">{{ h.selfTimePercent | number:'1.1-1' }}%</span>
            </div>
            <div>
              <span class="text-gray-400 text-xs block">Total Time</span>
              <span class="font-bold text-lg">{{ h.totalTimePercent | number:'1.1-1' }}%</span>
            </div>
          </div>
          @if (h.diagnosis) {
            <div class="bg-amber-50 dark:bg-amber-900/20 rounded p-3">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs px-1.5 py-0.5 rounded" [class]="severityBadge(h.severity!)">{{ h.severity }}</span>
                <span class="text-xs text-gray-400">Built-in</span>
              </div>
              <p class="text-xs text-amber-800 dark:text-amber-200">{{ h.diagnosis }}</p>
            </div>
          }
          @if (h.topCallerSignature) {
            <div>
              <span class="text-gray-400 text-xs block">Top Caller</span>
              <span class="font-mono text-xs break-all text-blue-600">{{ h.topCallerSignature }}</span>
            </div>
          }
        </div>
      }
    </div>

    <!-- Diagnoses list -->
    @if (diagnoses().length > 0) {
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <h3 class="font-semibold mb-2">All Diagnoses</h3>
        <ul class="space-y-1">
          @for (d of diagnoses(); track d.ruleId) {
            <li class="text-sm flex gap-3 items-start">
              <span class="text-xs px-1.5 py-0.5 rounded flex-shrink-0 mt-0.5" [class]="severityBadge(d.severity)">{{ d.severity }}</span>
              <span>{{ d.message }}</span>
            </li>
          }
        </ul>
      </div>
    }
  }
</div>
  `,
})
export class HotspotsComponent {
  selected = signal<HotspotDto | null>(null);
  sortKey = signal<keyof HotspotDto>('rank');
  sortAsc = signal(true);

  private r = computed(() => this.store.getActiveResult());
  hotspots = computed(() => this.r()?.hotspots ?? []);
  diagnoses = computed(() => this.r()?.diagnoses ?? []);
  confirmedDeadlock = computed(() => this.r()?.timeline?.confirmedDeadlock ?? false);
  sessionId = computed(() => this.r()?.sessionId ?? null);

  sorted = computed(() => {
    const key = this.sortKey();
    const asc = this.sortAsc();
    return [...this.hotspots()].sort((a, b) => {
      const av = a[key] as any, bv = b[key] as any;
      return asc ? (av > bv ? 1 : -1) : (av < bv ? 1 : -1);
    });
  });

  constructor(readonly store: SessionStoreService, readonly api: ApiService, readonly i18n: I18nService) {}

  sort(key: keyof HotspotDto): void {
    if (this.sortKey() === key) this.sortAsc.update(v => !v);
    else { this.sortKey.set(key); this.sortAsc.set(false); }
  }

  select(h: HotspotDto): void { this.selected.set(h); }

  shortSig(sig: string): string {
    const parts = sig.split('.');
    if (parts.length <= 3) return sig;
    return '…' + parts.slice(-3).join('.');
  }

  heatColor(pct: number): string {
    if (pct >= 20) return 'text-red-600 dark:text-red-400';
    if (pct >= 10) return 'text-orange-500 dark:text-orange-400';
    if (pct >= 5) return 'text-amber-500 dark:text-amber-400';
    return 'text-gray-700 dark:text-gray-300';
  }

  layerColor(layer: string): string {
    const map: Record<string, string> = {
      'Database / Network': 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
      'ORM': 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
      'JSON': 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
      'Cryptography': 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
      'HTTP / Web': 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200',
      'JDK Core': 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
      'Business Logic': 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
    };
    return map[layer] ?? 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400';
  }

  severityBadge(sev: string): string {
    return sev === 'CRITICAL' ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
         : sev === 'WARNING' ? 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'
         : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300';
  }
}
