import { LiveAnnouncer } from '@angular/cdk/a11y';
import { DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { AuthUser } from '../../../core/auth/auth.service';
import { ChatEvent, ConversationView, MessageView } from '../chat.models';
import { ChatSocketService } from '../chat-socket.service';
import { SupportApiService } from '../support-api.service';

const MAX_LENGTH = 2000;
const TYPING_REFRESH_MS = 2000;
const TYPING_IDLE_MS = 3000;
const TYPING_DISPLAY_MS = 5000;

/**
 * A chat conversation: history, live messages, typing indicator, sending.
 * Used identically by the customer and the agent.
 *
 * Accessibility: a new message from the other participant is announced by {@link LiveAnnouncer}
 * (screen reader) without moving focus; everything works with the keyboard (Enter sends, Shift+Enter = new line).
 */
@Component({
  selector: 'app-chat-panel',
  imports: [DatePipe],
  templateUrl: './chat-panel.html',
  styleUrl: './chat-panel.scss',
})
export class ChatPanel {
  readonly conversation = input.required<ConversationView>();
  readonly me = input.required<AuthUser>();
  /** Emitted when the status changes (claim, close) so the parent page can update its lists. */
  readonly conversationChange = output<ConversationView>();

  protected readonly chatSocket = inject(ChatSocketService);
  private readonly supportApi = inject(SupportApiService);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly current = linkedSignal(() => this.conversation());
  protected readonly messages = signal<MessageView[]>([]);
  /** Sent messages whose confirmation (server echo after saving) has not arrived yet. */
  protected readonly pending = signal<string[]>([]);
  protected readonly typingName = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly maxLength = MAX_LENGTH;

  private readonly messageList = viewChild<ElementRef<HTMLElement>>('messageList');
  private live?: Subscription;
  private typingSentAt = 0;
  private typingIdleTimer?: ReturnType<typeof setTimeout>;
  private typingDisplayTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Each conversation change restarts the subscription and the history loading.
    effect((onCleanup) => {
      const id = this.conversation().id;
      untracked(() => this.start(id));
      onCleanup(() => this.stop());
    });

    this.chatSocket.errors$.pipe(takeUntilDestroyed()).subscribe((e) => {
      this.error.set(e.message);
      this.restoreLastPending();
    });
    // After a disconnection: fetch the messages received in the meantime; the draft being typed is left untouched.
    this.chatSocket.reconnected$.pipe(takeUntilDestroyed()).subscribe(() => this.loadMissed());
    this.destroyRef.onDestroy(() => this.stop());
  }

  protected get isClosed(): boolean {
    return this.current().status === 'CLOSED';
  }

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
    this.error.set(null);
    this.notifyTyping();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  protected sendMessage(): void {
    const content = this.draft().trim();
    if (!content || this.isClosed) {
      return;
    }
    const id = this.current().id;
    if (!this.chatSocket.publish(`/app/conversations/${id}/messages`, { content })) {
      // Connection down: the input is not cleared, nothing is lost.
      this.error.set('Connexion interrompue, reconnexion en cours. Votre message est conservé : réessayez dans un instant.');
      return;
    }
    this.pending.update((list) => [...list, content]);
    this.draft.set('');
    this.error.set(null);
    this.stopTyping();
  }

  // --- Conversation lifecycle -----------------------------------------------------------------------------

  private start(id: number): void {
    this.messages.set([]);
    this.pending.set([]);
    this.typingName.set(null);
    this.error.set(null);
    this.chatSocket.connect();
    // 1. subscribe first, 2. then load the history: no message can slip through in between
    this.live = this.chatSocket.watch<ChatEvent>(`/topic/conversations.${id}`).subscribe((event) => this.onEvent(event));
    this.supportApi.getMessages(id).subscribe((list) => this.mergeMessages(list));
  }

  private stop(): void {
    this.live?.unsubscribe();
    this.live = undefined;
    clearTimeout(this.typingIdleTimer);
    clearTimeout(this.typingDisplayTimer);
  }

  private loadMissed(): void {
    const last = this.messages().at(-1)?.id;
    this.supportApi.getMessages(this.current().id, last).subscribe((list) => this.mergeMessages(list));
  }

  // --- Received events --------------------------------------------------------------------------------------------

  private onEvent(event: ChatEvent): void {
    switch (event.type) {
      case 'MESSAGE':
        if (event.message) {
          this.onMessage(event.message);
        }
        break;
      case 'TYPING':
        this.onTyping(event);
        break;
      case 'CLAIMED':
      case 'CLOSED':
        if (event.conversation) {
          this.current.set(event.conversation);
          this.conversationChange.emit(event.conversation);
          void this.announcer.announce(
            event.type === 'CLAIMED'
              ? `${event.conversation.agentName} a pris votre demande en charge`
              : 'La conversation a été clôturée',
          );
        }
        break;
    }
  }

  private onMessage(message: MessageView): void {
    const known = this.messages().some((m) => m.id === message.id);
    this.mergeMessages([message]);
    if (message.authorId === this.me().id) {
      this.pending.update((list) => list.slice(1)); // confirmed: saved by the server
    } else if (!known) {
      this.typingName.set(null);
      void this.announcer.announce(`${message.authorName} : ${message.content}`);
    }
  }

  private onTyping(event: ChatEvent): void {
    if (event.actorId === this.me().id) {
      return;
    }
    clearTimeout(this.typingDisplayTimer);
    if (event.typing) {
      this.typingName.set(event.actorName);
      this.typingDisplayTimer = setTimeout(() => this.typingName.set(null), TYPING_DISPLAY_MS);
    } else {
      this.typingName.set(null);
    }
  }

  private mergeMessages(incoming: MessageView[]): void {
    this.messages.update((current) => {
      const byId = new Map(current.map((m) => [m.id, m]));
      incoming.forEach((m) => byId.set(m.id, m));
      return [...byId.values()].sort((a, b) => a.id - b.id);
    });
    queueMicrotask(() => this.scrollToEnd());
  }

  private scrollToEnd(): void {
    const element = this.messageList()?.nativeElement;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }

  // --- "is typing" indicator ------------------------------------------------------------------------------

  private notifyTyping(): void {
    if (this.draft().length === 0) {
      this.stopTyping();
      return;
    }
    const now = Date.now();
    if (now - this.typingSentAt > TYPING_REFRESH_MS) {
      this.chatSocket.publish(`/app/conversations/${this.current().id}/typing`, { typing: true });
      this.typingSentAt = now;
    }
    clearTimeout(this.typingIdleTimer);
    this.typingIdleTimer = setTimeout(() => this.stopTyping(), TYPING_IDLE_MS);
  }

  private stopTyping(): void {
    clearTimeout(this.typingIdleTimer);
    if (this.typingSentAt !== 0) {
      this.chatSocket.publish(`/app/conversations/${this.current().id}/typing`, { typing: false });
      this.typingSentAt = 0;
    }
  }

  /** The server rejected the message: put it back in the input so it is not lost. */
  private restoreLastPending(): void {
    const list = this.pending();
    if (list.length > 0) {
      this.draft.set(list.at(-1) ?? '');
      this.pending.set([]);
    }
  }
}
