import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ChatEvent, ConversationView, MessageView } from '../chat.models';
import { ChatSocketService } from '../chat-socket.service';
import { SupportApiService } from '../support-api.service';
import { ChatPanel } from './chat-panel';

const conversation: ConversationView = {
  id: 7,
  status: 'OPEN',
  clientId: 1,
  clientName: 'Camille D.',
  agentId: 3,
  agentName: 'Sophie M.',
  reservationId: null,
  createdAt: '2026-10-15T10:00:00Z',
  closedAt: null,
};

const message = (id: number, authorId: number, content: string): MessageView => ({
  id,
  conversationId: 7,
  authorId,
  authorName: authorId === 1 ? 'Camille D.' : 'Sophie M.',
  content,
  sentAt: '2026-10-15T10:00:00Z',
});

describe('ChatPanel', () => {
  let fixture: ComponentFixture<ChatPanel>;
  let live: Subject<ChatEvent>;
  let announce: ReturnType<typeof vi.fn>;
  let publish: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    live = new Subject<ChatEvent>();
    announce = vi.fn();
    publish = vi.fn().mockReturnValue(true);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: ChatSocketService,
          useValue: {
            connected: signal(true),
            connect: vi.fn(),
            watch: () => live.asObservable(),
            publish,
            errors$: new Subject(),
            reconnected$: new Subject(),
          },
        },
        { provide: SupportApiService, useValue: { getMessages: () => of([message(1, 1, 'Bonjour')]) } },
        { provide: LiveAnnouncer, useValue: { announce } },
      ],
    });
    fixture = TestBed.createComponent(ChatPanel);
    fixture.componentRef.setInput('conversation', conversation);
    fixture.componentRef.setInput('me', { id: 1, displayName: 'Camille D.', role: 'CLIENT' });
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it("affiche l'historique chargé par REST", async () => {
    await fixture.whenStable();
    expect(text()).toContain('Bonjour');
  });

  it("ajoute un message reçu en direct et l'annonce aux lecteurs d'écran sans doublon", async () => {
    const event: ChatEvent = {
      type: 'MESSAGE',
      conversationId: 7,
      message: message(2, 3, 'Je regarde cela'),
      conversation: null,
      actorId: 3,
      actorName: 'Sophie M.',
      typing: null,
    };

    live.next(event);
    live.next(event); // same message received twice (e.g. catch-up after reconnection)
    await fixture.whenStable();

    const occurrences = text().split('Je regarde cela').length - 1;
    expect(occurrences).toBe(1);
    expect(announce).toHaveBeenCalledTimes(1);
    expect(announce).toHaveBeenCalledWith('Sophie M. : Je regarde cela');
  });

  it('envoie le message vers /app/conversations/{id}/messages et vide la saisie', async () => {
    const textarea = (fixture.nativeElement as HTMLElement).querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '  Merci !  ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(publish).toHaveBeenCalledWith('/app/conversations/7/messages', { content: 'Merci !' });
  });

  it('garde le message dans la zone de saisie quand la connexion est coupée', async () => {
    publish.mockReturnValue(false);
    const textarea = (fixture.nativeElement as HTMLElement).querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Toujours là ?';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textarea.value).toBe('Toujours là ?');
    expect(text()).toContain('Votre message est conservé');
  });
});
