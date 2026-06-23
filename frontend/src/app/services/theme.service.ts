import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'pa.theme';

  isDark = signal<boolean>(false);

  constructor() {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    const dark = stored === 'dark';
    this.isDark.set(dark);
    this.applyClass(dark);
  }

  toggle(): void {
    const next = !this.isDark();
    this.isDark.set(next);
    localStorage.setItem(this.STORAGE_KEY, next ? 'dark' : 'light');
    this.applyClass(next);
  }

  private applyClass(dark: boolean): void {
    document.documentElement.classList.toggle('dark', dark);
  }
}
