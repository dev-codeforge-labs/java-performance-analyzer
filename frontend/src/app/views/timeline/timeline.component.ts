import { Component, computed, OnInit, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionStoreService } from '../../services/session-store.service';
import { ApiService } from '../../services/api.service';
import { I18nService } from '../../services/i18n.service';

@Component({
  selector: 'app-timeline',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="max-w-6xl mx-auto space-y-6">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('timeline.title') }}</h1>
    <div class="flex items-center gap-2">
      @if (confirmedDeadlock()) {
        <span class="bg-red-600 text-white text-xs px-3 py-1 rounded-full font-semibold animate-pulse">
          ⚠ {{ i18n.t('timeline.confirmedDeadlock') }}
        </span>
      }
      @if (sessionId()) {
        <a [href]="api.exportPdfUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-red-600 text-white hover:bg-red-700">⬇ {{ i18n.t('export.pdf') }}</a>
        <a [href]="api.exportDocxUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-blue-700 text-white hover:bg-blue-800">⬇ {{ i18n.t('export.docx') }}</a>
      }
    </div>
  </div>

  @if (!timeline()) {
    <p class="text-gray-500">{{ i18n.t('timeline.noData') }}</p>
  } @else {
    <!-- Thread State Timeline Chart -->
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <h2 class="font-semibold mb-3">Thread State Distribution</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-xs">
          <thead>
            <tr class="text-gray-400">
              <th class="text-left px-2 py-1">Snapshot</th>
              <th class="px-2 py-1">Threads</th>
              <th class="px-2 py-1 text-green-600">RUNNABLE</th>
              <th class="px-2 py-1 text-red-600">BLOCKED</th>
              <th class="px-2 py-1 text-blue-600">WAITING</th>
              <th class="px-2 py-1 text-purple-600">TIMED_WAITING</th>
            </tr>
          </thead>
          <tbody>
            @for (s of timeline()!.snapshots; track s.index) {
              <tr class="border-t border-gray-100 dark:border-gray-800">
                <td class="px-2 py-1 text-gray-500">t{{ s.index }}</td>
                <td class="px-2 py-1 text-center">{{ s.totalThreads }}</td>
                <td class="px-2 py-1">
                  <div class="flex items-center gap-1">
                    <div class="h-2 rounded bg-green-500" [style.width.px]="s.runnablePercent * 1.2"></div>
                    <span>{{ s.runnablePercent | number:'1.0-0' }}%</span>
                  </div>
                </td>
                <td class="px-2 py-1">
                  <div class="flex items-center gap-1">
                    <div class="h-2 rounded bg-red-500" [style.width.px]="s.blockedPercent * 1.2"></div>
                    <span [class.font-bold]="s.blockedPercent > 20" [class.text-red-600]="s.blockedPercent > 20">
                      {{ s.blockedPercent | number:'1.0-0' }}%
                    </span>
                  </div>
                </td>
                <td class="px-2 py-1">
                  <div class="flex items-center gap-1">
                    <div class="h-2 rounded bg-blue-500" [style.width.px]="s.waitingPercent * 1.2"></div>
                    <span>{{ s.waitingPercent | number:'1.0-0' }}%</span>
                  </div>
                </td>
                <td class="px-2 py-1">
                  <div class="flex items-center gap-1">
                    <div class="h-2 rounded bg-purple-500" [style.width.px]="s.timedWaitingPercent * 1.2"></div>
                    <span>{{ s.timedWaitingPercent | number:'1.0-0' }}%</span>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <!-- Hotspot Trends -->
    @if ((timeline()?.hotspotTrends?.length ?? 0) > 0) {
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <h2 class="font-semibold mb-3">Hotspot Trends (Top 10)</h2>
        <div class="space-y-2">
          @for (ht of timeline()!.hotspotTrends; track ht.methodSignature) {
            <div class="flex items-center gap-3 text-xs">
              <span class="font-mono w-64 truncate text-gray-600 dark:text-gray-400" [title]="ht.methodSignature">
                {{ shortSig(ht.methodSignature) }}
              </span>
              <div class="flex gap-1">
                @for (pct of ht.selfTimePercents; track $index) {
                  <div class="w-8 text-center rounded py-0.5" [class]="trendCell(pct)" [title]="pct + '%'">
                    {{ pct | number:'1.0-0' }}%
                  </div>
                }
              </div>
            </div>
          }
        </div>
      </div>
    }

    <!-- Swim Lanes (top 20 threads) -->
    @if ((timeline()?.swimLanes?.length ?? 0) > 0) {
      <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <h2 class="font-semibold mb-3">Thread Swim Lanes <span class="text-sm font-normal text-gray-400">(first 20 threads)</span></h2>
        <div class="overflow-x-auto">
          <table class="text-xs">
            <thead>
              <tr>
                <th class="text-left px-2 py-1 w-48 text-gray-400">Thread</th>
                @for (s of timeline()!.snapshots; track s.index) {
                  <th class="px-1 py-1 text-gray-400 text-center w-10">t{{ s.index }}</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (lane of timeline()!.swimLanes.slice(0, 20); track lane.threadId) {
                <tr class="border-t border-gray-100 dark:border-gray-800">
                  <td class="px-2 py-1 truncate w-48 text-gray-600 dark:text-gray-400" [title]="lane.threadName">
                    {{ lane.threadName }}
                  </td>
                  @for (cell of lane.cells; track cell.snapshotIndex) {
                    <td class="px-1 py-1 text-center">
                      <div class="w-8 h-4 rounded text-center text-xs leading-4" [class]="stateColor(cell.state)" [title]="cell.state + (cell.topFrame ? ': ' + cell.topFrame : '')">
                        {{ stateInitial(cell.state) }}
                      </div>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="flex gap-4 mt-2 text-xs text-gray-500">
          <span><span class="inline-block w-3 h-3 rounded bg-green-200 dark:bg-green-800"></span> R=Runnable</span>
          <span><span class="inline-block w-3 h-3 rounded bg-red-200 dark:bg-red-800"></span> B=Blocked</span>
          <span><span class="inline-block w-3 h-3 rounded bg-blue-200 dark:bg-blue-800"></span> W=Waiting</span>
          <span><span class="inline-block w-3 h-3 rounded bg-purple-200 dark:bg-purple-800"></span> T=Timed</span>
        </div>
      </div>
    }
  }
</div>
  `,
})
export class TimelineComponent {
  private r = computed(() => this.store.getActiveResult());
  timeline = computed(() => this.r()?.timeline ?? null);
  confirmedDeadlock = computed(() => this.timeline()?.confirmedDeadlock ?? false);
  sessionId = computed(() => this.r()?.sessionId ?? null);

  constructor(readonly store: SessionStoreService, readonly api: ApiService, readonly i18n: I18nService) {}

  shortSig(sig: string): string {
    const parts = sig.split('.');
    return parts.length > 3 ? '…' + parts.slice(-3).join('.') : sig;
  }

  trendCell(pct: number): string {
    if (pct >= 20) return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300';
    if (pct >= 10) return 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300';
    if (pct > 0) return 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400';
    return 'bg-gray-50 dark:bg-gray-900 text-gray-300 dark:text-gray-700';
  }

  stateColor(state: string): string {
    return state === 'RUNNABLE' ? 'bg-green-200 dark:bg-green-800 text-green-800 dark:text-green-200'
         : state === 'BLOCKED' ? 'bg-red-200 dark:bg-red-800 text-red-800 dark:text-red-200'
         : state === 'WAITING' ? 'bg-blue-200 dark:bg-blue-800 text-blue-800 dark:text-blue-200'
         : state === 'TIMED_WAITING' ? 'bg-purple-200 dark:bg-purple-800 text-purple-800 dark:text-purple-200'
         : 'bg-gray-100 dark:bg-gray-800 text-gray-400';
  }

  stateInitial(state: string): string {
    return state === 'RUNNABLE' ? 'R' : state === 'BLOCKED' ? 'B'
         : state === 'WAITING' ? 'W' : state === 'TIMED_WAITING' ? 'T' : '?';
  }
}
