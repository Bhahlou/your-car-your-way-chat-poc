import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/auth/auth.service';
import { ChatSocketService } from '../chat-socket.service';
import { ConversationView, QueueEvent } from '../chat.models';
import { SupportApiService } from '../support-api.service';
import { ChatPanel } from '../shared/chat-panel';

/**
 * Agent console (US-8.4, 8.5): real-time waiting queue, claim, reply, close.
 * This console's code is only loaded for the AGENT role (lazy-loaded route).
 */
@Component({
  selector: 'app-agent-console',
  imports: [ChatPanel, DatePipe],
  templateUrl: './agent-console.html',
  styleUrls: ['../shared/two-columns.scss', './agent-console.scss'],
})
export class AgentConsole implements OnInit {
  private readonly supportApi = inject(SupportApiService);
  private readonly chatSocket = inject(ChatSocketService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly user = inject(AuthService).user;

  protected readonly queue = signal<ConversationView[]>([]);
  protected readonly assigned = signal<ConversationView[]>([]);
  protected readonly selected = signal<ConversationView | null>(null);
  protected readonly message = signal<string | null>(null);

  ngOnInit(): void {
    this.chatSocket.connect();
    this.refreshConversationLists();
    // The queue updates without reloading: new request, or request claimed by a colleague.
    this.chatSocket
      .watch<QueueEvent>('/topic/support.queue')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onQueueEvent(event));
    this.chatSocket.reconnected$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshConversationLists());
  }

  protected claimConversation(conversation: ConversationView): void {
    this.supportApi.claimConversation(conversation.id).subscribe({
      next: (claimed) => {
        this.message.set(null);
        this.queue.update((list) => list.filter((c) => c.id !== claimed.id));
        this.assigned.update((list) => [...list.filter((c) => c.id !== claimed.id), claimed]);
        this.selected.set(claimed);
      },
      error: (error: HttpErrorResponse) => {
        this.message.set(
          error.status === 409
            ? 'Cette conversation vient d\'être prise en charge par un autre conseiller.'
            : 'Prise en charge impossible, veuillez réessayer.',
        );
        this.refreshConversationLists();
      },
    });
  }

  protected closeConversation(conversation: ConversationView): void {
    this.supportApi.closeConversation(conversation.id).subscribe((closed) => {
      this.onConversationChange(closed);
      this.assigned.update((list) => list.filter((c) => c.id !== closed.id));
    });
  }

  protected selectConversation(conversation: ConversationView): void {
    this.selected.set(conversation);
  }

  protected onConversationChange(conversation: ConversationView): void {
    if (this.selected()?.id === conversation.id) {
      this.selected.set(conversation);
    }
  }

  private refreshConversationLists(): void {
    this.supportApi.getWaitingQueue().subscribe((list) => this.queue.set(list));
    this.supportApi.getAssignedConversations().subscribe((list) => this.assigned.set(list));
  }

  private onQueueEvent(event: QueueEvent): void {
    const { conversation } = event;
    if (event.type === 'WAITING') {
      this.queue.update((list) =>
        list.some((c) => c.id === conversation.id) ? list : [...list, conversation],
      );
    } else {
      this.queue.update((list) => list.filter((c) => c.id !== conversation.id));
    }
  }
}
