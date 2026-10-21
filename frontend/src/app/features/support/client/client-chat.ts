import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ConversationView } from '../chat.models';
import { SupportApiService } from '../support-api.service';
import { ChatPanel } from '../shared/chat-panel';

/**
 * Customer area of the chat (US-8.1, 8.2, 8.3): open a conversation, write to an agent, find the history.
 * Opened from a reservation's detail page, the URL carries ?reservationId=... and the conversation is linked to it.
 */
@Component({
  selector: 'app-client-chat',
  imports: [ChatPanel, DatePipe],
  templateUrl: './client-chat.html',
  styleUrls: ['../shared/two-columns.scss', './client-chat.scss'],
})
export class ClientChat implements OnInit {
  private readonly supportApi = inject(SupportApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly user = inject(AuthService).user;

  protected readonly conversations = signal<ConversationView[]>([]);
  protected readonly selected = signal<ConversationView | null>(null);
  protected readonly reservationId = signal(this.route.snapshot.queryParamMap.get('reservationId') ?? '');
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.supportApi.getMyConversations().subscribe((list) => {
      this.conversations.set(list);
      // automatically resume the ongoing conversation, if there is one
      this.selected.set(list.find((c) => c.status !== 'CLOSED') ?? null);
    });
  }

  protected get hasActiveConversation(): boolean {
    return this.conversations().some((c) => c.status !== 'CLOSED');
  }

  protected openConversation(): void {
    const parsed = Number.parseInt(this.reservationId(), 10);
    this.supportApi.openConversation(Number.isNaN(parsed) ? null : parsed).subscribe({
      next: (conversation) => {
        this.error.set(null);
        this.upsert(conversation);
        this.selected.set(conversation);
      },
      error: () => this.error.set("Impossible d'ouvrir la conversation, veuillez réessayer."),
    });
  }

  protected selectConversation(conversation: ConversationView): void {
    this.selected.set(conversation);
  }

  protected onConversationChange(conversation: ConversationView): void {
    this.upsert(conversation);
  }

  protected onReservationInput(event: Event): void {
    this.reservationId.set((event.target as HTMLInputElement).value);
  }

  private upsert(conversation: ConversationView): void {
    this.conversations.update((list) => [conversation, ...list.filter((c) => c.id !== conversation.id)]);
  }
}
