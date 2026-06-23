import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RulesetService } from '../../services/ruleset.service';
import { I18nService, Lang, LANG_LABELS } from '../../services/i18n.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
<div class="max-w-4xl mx-auto space-y-6">
  <div class="flex items-center justify-between">
    <h1 class="text-2xl font-bold">{{ i18n.t('settings.title') }}</h1>
    <div class="flex gap-2">
      <button (click)="rulesetService.exportJson()" class="text-sm px-3 py-1.5 rounded border border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800">
        {{ i18n.t('settings.exportRuleset') }}
      </button>
      <label class="text-sm px-3 py-1.5 rounded border border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800 cursor-pointer">
        {{ i18n.t('settings.importRuleset') }}
        <input type="file" accept=".json" class="hidden" (change)="importRuleset($event)">
      </label>
      <button (click)="rulesetService.reset()" class="text-sm px-3 py-1.5 rounded border border-red-300 dark:border-red-600 text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20">
        {{ i18n.t('settings.resetDefaults') }}
      </button>
    </div>
  </div>

  <!-- Language selector -->
  <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
    <h2 class="font-semibold mb-3">{{ i18n.t('settings.language') }}</h2>
    <div class="flex flex-wrap gap-2">
      @for (lang of langs; track lang) {
        <button
          (click)="i18n.setLang(lang)"
          [class]="lang === i18n.currentLang()
            ? 'px-4 py-2 rounded-lg text-sm font-semibold bg-blue-600 text-white border border-blue-600'
            : 'px-4 py-2 rounded-lg text-sm border border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'">
          {{ langLabels[lang] }}
        </button>
      }
    </div>
  </div>

  <!-- Custom Signatures -->
  <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
    <div class="flex items-center justify-between mb-3">
      <h2 class="font-semibold">{{ i18n.t('settings.signatures') }}</h2>
      <button (click)="addSignature()" class="text-sm px-3 py-1 rounded bg-blue-600 text-white hover:bg-blue-700">{{ i18n.t('settings.addSignature') }}</button>
    </div>
    @if (!(ruleset().customSignatures?.length)) {
      <p class="text-sm text-gray-400">{{ i18n.t('settings.noSignatures') }}</p>
    }
    @for (sig of ruleset().customSignatures; track sig; let i = $index) {
      <div class="grid grid-cols-4 gap-2 mb-2 items-end">
        <div>
          <label class="text-xs text-gray-400 block mb-0.5">{{ i18n.t('settings.packagePrefix') }}</label>
          <input [(ngModel)]="sig.prefix" (ngModelChange)="save()"
                 class="w-full text-xs border border-gray-300 dark:border-gray-600 rounded px-2 py-1.5 bg-white dark:bg-gray-800" placeholder="com.mycompany">
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-0.5">{{ i18n.t('hotspots.layer') }}</label>
          <input [(ngModel)]="sig.layer" (ngModelChange)="save()"
                 class="w-full text-xs border border-gray-300 dark:border-gray-600 rounded px-2 py-1.5 bg-white dark:bg-gray-800" placeholder="Business Logic">
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-0.5">{{ i18n.t('settings.diagMessage') }}</label>
          <input [(ngModel)]="sig.diagnosisMessage" (ngModelChange)="save()"
                 class="w-full text-xs border border-gray-300 dark:border-gray-600 rounded px-2 py-1.5 bg-white dark:bg-gray-800">
        </div>
        <div class="flex items-end gap-1">
          <select [(ngModel)]="sig.severity" (ngModelChange)="save()"
                  class="text-xs border border-gray-300 dark:border-gray-600 rounded px-2 py-1.5 bg-white dark:bg-gray-800">
            <option>WARNING</option>
            <option>CRITICAL</option>
            <option>INFO</option>
          </select>
          <button (click)="removeSignature(i)" class="text-red-500 hover:text-red-700 px-2 py-1.5">✕</button>
        </div>
      </div>
    }
  </div>

  <!-- Thresholds -->
  <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
    <h2 class="font-semibold mb-3">{{ i18n.t('settings.thresholds') }}</h2>
    <div class="space-y-4">
      @for (t of thresholdDefs; track t.key) {
        <div class="flex items-center gap-4">
          <label class="text-sm w-56 text-gray-700 dark:text-gray-300">{{ t.label }}</label>
          <input type="range" [min]="t.min" [max]="t.max" [step]="t.step"
                 [ngModel]="getThreshold(t.key)"
                 (ngModelChange)="setThreshold(t.key, $event)"
                 class="flex-1 accent-blue-600">
          <span class="text-sm font-mono w-12 text-right">{{ getThreshold(t.key) }}%</span>
        </div>
      }
    </div>
  </div>

  <!-- localStorage status -->
  <div class="text-xs text-gray-400 flex gap-2 items-center">
    <span class="w-2 h-2 rounded-full bg-green-500"></span>
    {{ i18n.t('settings.localStorageStatus') }} (key: <code>pa.ruleset</code>)
    · v{{ ruleset().version }}
  </div>
</div>
  `,
})
export class SettingsComponent {
  ruleset = computed(() => this.rulesetService.ruleset());
  langs: Lang[] = ['en', 'es', 'fr', 'de', 'pt', 'it'];
  langLabels = LANG_LABELS;

  thresholdDefs = [
    { key: 'min-self-time-critical', label: 'Min Self% for critical hotspot', min: 1, max: 50, step: 1 },
    { key: 'blocked-threads-alert', label: 'Blocked threads % alert', min: 5, max: 80, step: 5 },
    { key: 'gc-thrash-alert', label: 'GC time % alert', min: 5, max: 50, step: 5 },
    { key: 'calltree-delta-threshold', label: 'Call tree delta threshold (%)', min: 1, max: 20, step: 1 },
  ];

  constructor(readonly rulesetService: RulesetService, readonly i18n: I18nService) {}

  getThreshold(key: string): number { return this.ruleset().thresholds?.[key] ?? 0; }

  setThreshold(key: string, value: number): void {
    const r = { ...this.ruleset() };
    r.thresholds = { ...r.thresholds, [key]: Number(value) };
    this.rulesetService.save(r);
  }

  addSignature(): void {
    const r = { ...this.ruleset() };
    r.customSignatures = [...(r.customSignatures ?? []),
      { prefix: '', layer: 'Business Logic', diagnosisMessage: '', severity: 'WARNING' }];
    this.rulesetService.save(r);
  }

  removeSignature(index: number): void {
    const r = { ...this.ruleset() };
    r.customSignatures = r.customSignatures.filter((_, i) => i !== index);
    this.rulesetService.save(r);
  }

  save(): void { this.rulesetService.save({ ...this.ruleset() }); }

  importRuleset(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.rulesetService.importFromFile(file);
  }
}
