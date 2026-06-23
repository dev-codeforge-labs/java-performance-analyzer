import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', loadComponent: () => import('./views/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'call-tree', loadComponent: () => import('./views/call-tree/call-tree.component').then(m => m.CallTreeComponent) },
  { path: 'hotspots', loadComponent: () => import('./views/hotspots/hotspots.component').then(m => m.HotspotsComponent) },
  { path: 'timeline', loadComponent: () => import('./views/timeline/timeline.component').then(m => m.TimelineComponent) },
  { path: 'diff', loadComponent: () => import('./views/diff/diff.component').then(m => m.DiffComponent) },
  { path: 'heap', loadComponent: () => import('./views/heap/heap.component').then(m => m.HeapComponent) },
  { path: 'gc', loadComponent: () => import('./views/gc/gc.component').then(m => m.GcComponent) },
  { path: 'settings', loadComponent: () => import('./views/settings/settings.component').then(m => m.SettingsComponent) },
];
