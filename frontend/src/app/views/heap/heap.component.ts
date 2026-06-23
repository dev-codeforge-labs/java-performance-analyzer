import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-heap',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="max-w-6xl mx-auto space-y-4">
  <h1 class="text-2xl font-bold">Heap Dump Analyzer</h1>
  <div class="bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-700 rounded p-4 text-sm text-amber-800 dark:text-amber-200">
    <strong>Module 4 — Heap Dump Analyzer</strong> requires the Eclipse MAT API dependency.
    Upload a <code>.hprof</code> file from the Dashboard to activate this view.
    The dominator tree, class histogram, and leak candidate analysis will appear here once a heap dump is parsed.
  </div>
</div>
  `,
})
export class HeapComponent {}
