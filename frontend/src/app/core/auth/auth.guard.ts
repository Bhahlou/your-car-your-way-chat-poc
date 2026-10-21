import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

/**
 * Restricts a route to a role (customer or agent). This is a UI convenience: the real security
 * is on the server, which rejects (403) any request from an unauthorized role.
 */
export const roleGuard = (role: Role): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();

  if (!user || !auth.token()) {
    return router.createUrlTree(['/login']);
  }
  return user.role === role ? true : router.createUrlTree([auth.homeFor(user)]);
};
