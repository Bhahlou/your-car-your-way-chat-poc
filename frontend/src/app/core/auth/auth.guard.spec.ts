import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { roleGuard } from './auth.guard';

describe('roleGuard', () => {
  /** Simulates a signed-in user: AuthService reads its session back from sessionStorage when created. */
  function signInAs(role: 'CLIENT' | 'AGENT'): void {
    sessionStorage.setItem(
      'ycyw.poc.session',
      JSON.stringify({ token: 't', user: { id: 1, displayName: 'Test', role }, expiresAt: Date.now() + 60_000 }),
    );
  }

  const run = (role: 'CLIENT' | 'AGENT') =>
    TestBed.runInInjectionContext(() => roleGuard(role)({} as never, {} as never));

  beforeEach(() => {
    sessionStorage.clear();
  });

  function configure(): void {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  }

  it("renvoie vers la connexion quand personne n'est connecté", () => {
    configure();

    const result = run('CLIENT') as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login');
  });

  it('laisse passer le bon rôle', () => {
    signInAs('CLIENT');
    configure();

    expect(run('CLIENT')).toBe(true);
  });

  it("redirige un client qui tente d'ouvrir la console conseiller vers son espace", () => {
    signInAs('CLIENT');
    configure();

    const redirect = run('AGENT') as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(redirect)).toBe('/support');
  });
});
