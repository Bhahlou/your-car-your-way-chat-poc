import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

interface DemoAccount {
  label: string;
  email: string;
}

/** PoC login page (US-1.3 reduced to the essentials). Demo accounts are created by the back end. */
@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly error = signal<string | null>(null);

  protected readonly demoAccounts: DemoAccount[] = [
    { label: 'Client 1 (Camille)', email: 'client1@example.com' },
    { label: 'Client 2 (Jonas)', email: 'client2@example.com' },
    { label: 'Conseillère 1 (Sophie)', email: 'agent1@example.com' },
    { label: 'Conseiller 2 (Marc)', email: 'agent2@example.com' },
  ];

  protected fill(account: DemoAccount): void {
    this.email.set(account.email);
    this.password.set('Password123!');
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected submit(): void {
    this.auth.login(this.email(), this.password()).subscribe({
      next: (user) => void this.router.navigateByUrl(this.auth.homeFor(user)),
      // deliberately generic message: we do not say which of the two fields is wrong
      error: () => this.error.set('Email ou mot de passe incorrect.'),
    });
  }
}
