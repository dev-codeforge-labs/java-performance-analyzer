import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { ThemeService } from './services/theme.service';
import { I18nService } from './services/i18n.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
})
export class AppComponent {
  isDark = this.theme.isDark;

  constructor(private theme: ThemeService, readonly i18n: I18nService) {}

  toggleTheme(): void { this.theme.toggle(); }
}
