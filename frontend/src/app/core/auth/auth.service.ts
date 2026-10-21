import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

export type Role = 'CLIENT' | 'AGENT';

export interface AuthUser {
  id: number;
  displayName: string;
  role: Role;
}

interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: AuthUser;
}

interface Session {
  token: string;
  user: AuthUser;
  expiresAt: number;
}

const STORAGE_KEY = 'ycyw.poc.session';

/**
 * User session: short-lived JWT (15 min) returned by POST /api/v1/auth/login.
 *
 * PoC simplification: the token is kept in sessionStorage to survive a page reload.
 * The target architecture uses a refresh token in an HttpOnly cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);

  /** Token to send in the Authorization header, or null if missing or expired. */
  token(): string | null {
    const current = this.session();
    if (!current || current.expiresAt <= Date.now()) {
      return null;
    }
    return current.token;
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap((response) => this.store({
        token: response.accessToken,
        user: response.user,
        expiresAt: Date.now() + response.expiresIn * 1000,
      })),
      map((response) => response.user),
    );
  }

  logout(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable (private browsing...): the in-memory session is enough
    }
    void this.router.navigate(['/login']);
  }

  /** Home page by role: customer area or agent console. */
  homeFor(user: AuthUser): string {
    return user.role === 'AGENT' ? '/agent' : '/support';
  }

  private store(session: Session): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // same: carry on without persistence
    }
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      const session = raw ? (JSON.parse(raw) as Session) : null;
      return session && session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
