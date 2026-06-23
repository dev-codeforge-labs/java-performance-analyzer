import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SessionStoreService } from '../../services/session-store.service';
import { ApiService } from '../../services/api.service';
import { I18nService } from '../../services/i18n.service';
import { CallTreeNodeDto } from '../../models/analysis.models';

@Component({
  selector: 'app-call-tree',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
<div class="max-w-6xl mx-auto space-y-4">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('callTree.title') }}</h1>
    <div class="flex items-center gap-2">
      <input [(ngModel)]="searchTerm" [placeholder]="i18n.t('callTree.search')"
             class="text-sm border border-gray-300 dark:border-gray-600 rounded px-3 py-1.5 bg-white dark:bg-gray-800 w-56">
      <button (click)="inverted.set(!inverted())"
              class="text-sm px-3 py-1.5 rounded border border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800">
        {{ inverted() ? i18n.t('callTree.normal') : i18n.t('callTree.inverted') }}
      </button>
      @if (sessionId()) {
        <a [href]="api.exportPdfUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-red-600 text-white hover:bg-red-700">⬇ {{ i18n.t('export.pdf') }}</a>
        <a [href]="api.exportDocxUrl(sessionId()!)" target="_blank"
           class="text-xs px-2.5 py-1.5 rounded bg-blue-700 text-white hover:bg-blue-800">⬇ {{ i18n.t('export.docx') }}</a>
      }
    </div>
  </div>

  @if (!tree()) {
    <p class="text-gray-500">{{ i18n.t('callTree.noData') }}</p>
  } @else {
    <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4 overflow-auto">
      <ng-container *ngTemplateOutlet="nodeTemplate; context: { node: tree(), depth: 0 }"></ng-container>
    </div>
  }
</div>

<ng-template #nodeTemplate let-node="node" let-depth="depth">
  @if (node && node.methodSignature !== '[root]') {
    <div class="flex items-start gap-1 my-0.5" [style.padding-left.px]="depth * 16">
      <button (click)="toggle(node.methodSignature)"
              class="text-gray-400 hover:text-gray-600 w-4 flex-shrink-0 text-xs mt-0.5">
        {{ isCollapsed(node.methodSignature) ? '▶' : node.children?.length ? '▼' : ' ' }}
      </button>
      <div class="flex-1 min-w-0">
        <span class="font-mono text-xs"
              [class]="nodeColor(node.totalTimePercent)"
              [class.font-bold]="matchSearch(node.methodSignature)"
              [class.bg-yellow-100]="matchSearch(node.methodSignature)"
              [title]="node.methodSignature">
          {{ shortSig(node.methodSignature) }}
        </span>
        <span class="text-xs text-gray-400 ml-2">
          total={{ node.totalTimePercent | number:'1.1-1' }}%
          @if (node.selfTimePercent > 0) { self={{ node.selfTimePercent | number:'1.1-1' }}% }
        </span>
        <span class="text-xs ml-1 px-1 rounded" [class]="layerBadge(node.layerCategory)">{{ node.layerCategory }}</span>
      </div>
    </div>
    @if (!isCollapsed(node.methodSignature) && node.children?.length) {
      @for (child of node.children; track child.methodSignature) {
        <ng-container *ngTemplateOutlet="nodeTemplate; context: { node: child, depth: depth + 1 }"></ng-container>
      }
    }
  } @else if (node?.methodSignature === '[root]') {
    @for (child of node.children; track child.methodSignature) {
      <ng-container *ngTemplateOutlet="nodeTemplate; context: { node: child, depth: 0 }"></ng-container>
    }
  }
</ng-template>
  `,
})
export class CallTreeComponent {
  inverted = signal(false);
  searchTerm = '';
  collapsed = new Set<string>();

  private r = computed(() => this.store.getActiveResult());
  tree = computed(() => this.inverted() ? this.r()?.invertedCallTree : this.r()?.callTree);
  sessionId = computed(() => this.r()?.sessionId ?? null);

  constructor(readonly store: SessionStoreService, readonly api: ApiService, readonly i18n: I18nService) {}

  toggle(sig: string): void {
    if (this.collapsed.has(sig)) this.collapsed.delete(sig);
    else this.collapsed.add(sig);
  }

  isCollapsed(sig: string): boolean { return this.collapsed.has(sig); }

  matchSearch(sig: string): boolean {
    return this.searchTerm.length > 2 && sig.toLowerCase().includes(this.searchTerm.toLowerCase());
  }

  shortSig(sig: string): string {
    const parts = sig.split('.');
    if (parts.length <= 3) return sig;
    return '…' + parts.slice(-3).join('.');
  }

  nodeColor(pct: number): string {
    if (pct >= 30) return 'text-red-600 dark:text-red-400';
    if (pct >= 15) return 'text-orange-500 dark:text-orange-400';
    if (pct >= 5) return 'text-amber-500 dark:text-amber-400';
    return 'text-gray-800 dark:text-gray-200';
  }

  layerBadge(layer: string): string {
    return 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400';
  }
}
