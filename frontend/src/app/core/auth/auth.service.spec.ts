import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('garde le jeton et l\'utilisateur après une connexion réussie', () => {
    auth.login('client1@example.com', 'Password123!').subscribe();
    http.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-123',
      expiresIn: 900,
      user: { id: 1, displayName: 'Camille D.', role: 'CLIENT' },
    });

    expect(auth.token()).toBe('jwt-123');
    expect(auth.user()?.displayName).toBe('Camille D.');
  });

  it('ne renvoie plus de jeton une fois expiré', () => {
    auth.login('client1@example.com', 'Password123!').subscribe();
    http.expectOne('/api/v1/auth/login').flush({
      accessToken: 'jwt-123',
      expiresIn: -1,
      user: { id: 1, displayName: 'Camille D.', role: 'CLIENT' },
    });

    expect(auth.token()).toBeNull();
  });

  it('oriente chaque rôle vers son espace', () => {
    expect(auth.homeFor({ id: 1, displayName: 'A', role: 'CLIENT' })).toBe('/support');
    expect(auth.homeFor({ id: 2, displayName: 'B', role: 'AGENT' })).toBe('/agent');
  });
});
