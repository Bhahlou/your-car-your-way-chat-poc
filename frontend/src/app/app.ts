import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { ChatSocketService } from './features/support/chat-socket.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly chatSocket = inject(ChatSocketService);
  protected readonly user = this.auth.user;

  protected logout(): void {
    this.chatSocket.disconnect();
    this.auth.logout();
  }
}
