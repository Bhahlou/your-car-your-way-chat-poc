import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/auth.guard';
import { Login } from './features/login/login';
import { ClientChat } from './features/support/client/client-chat';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Connexion' },
  {
    path: 'support',
    component: ClientChat,
    canActivate: [roleGuard('CLIENT')],
    title: 'Service client',
  },
  {
    // Agent console loaded on demand: customers do not download this code (architecture §5.4)
    path: 'agent',
    loadComponent: () => import('./features/support/agent/agent-console').then((m) => m.AgentConsole),
    canActivate: [roleGuard('AGENT')],
    title: 'Console conseiller',
  },
  { path: '', pathMatch: 'full', redirectTo: 'support' },
  { path: '**', redirectTo: 'support' },
];
