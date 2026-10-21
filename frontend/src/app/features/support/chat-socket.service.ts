import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ChatError } from './chat.models';

interface Watch {
  destination: string;
  deliver: (body: unknown) => void;
  subscription?: StompSubscription;
}

/**
 * Single WebSocket/STOMP connection, shared by the whole application.
 *
 * - the JWT is sent in the CONNECT frame (the back end verifies it);
 * - reconnection is automatic, and subscriptions are recreated on each reconnection;
 * - {@link reconnected$} signals each (re)connection: components then reload missed messages over REST.
 *
 * The PoC's in-memory broker does not acknowledge subscriptions: to miss nothing, we subscribe first,
 * then load the history over REST, deduplicating by message id.
 */
@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private readonly auth = inject(AuthService);
  private client: Client | null = null;
  private readonly watches = new Set<Watch>();
  private readonly reconnectedSubject = new Subject<void>();
  private readonly errorSubject = new Subject<ChatError>();

  /** true while the connection is established. */
  readonly connected = signal(false);
  readonly reconnected$ = this.reconnectedSubject.asObservable();
  /** Business errors sent by the server to this user (empty message, closed conversation...). */
  readonly errors$ = this.errorSubject.asObservable();

  /** Opens the connection if it does not exist yet (idempotent). */
  connect(): void {
    if (this.client) {
      return;
    }
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${scheme}://${location.host}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // token re-read on each attempt: a reconnection after expiry does not reuse an old token
      beforeConnect: () => {
        const token = this.auth.token();
        if (!token) {
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        this.connected.set(true);
        this.watches.forEach((watch) => this.subscribe(watch));
        this.watchErrors();
        this.reconnectedSubject.next();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client = client;
    client.activate();
  }

  disconnect(): void {
    const client = this.client;
    this.client = null;
    this.watches.clear();
    this.connected.set(false);
    if (client) {
      void client.deactivate();
    }
  }

  /** Stream of messages for a destination (topic). Unsubscribing closes the STOMP subscription. */
  watch<T>(destination: string): Observable<T> {
    return new Observable<T>((subscriber) => {
      const watch: Watch = { destination, deliver: (body) => subscriber.next(body as T) };
      this.watches.add(watch);
      if (this.connected()) {
        this.subscribe(watch);
      }
      return () => {
        watch.subscription?.unsubscribe();
        this.watches.delete(watch);
      };
    });
  }

  /** Sends to an application destination. Returns false if the connection is down (nothing is lost on the caller's side). */
  publish(destination: string, body: unknown): boolean {
    if (!this.client || !this.connected()) {
      return false;
    }
    this.client.publish({
      destination,
      body: JSON.stringify(body),
      headers: { 'content-type': 'application/json' },
    });
    return true;
  }

  private subscribe(watch: Watch): void {
    watch.subscription = this.client?.subscribe(watch.destination, (message: IMessage) =>
      watch.deliver(JSON.parse(message.body)),
    );
  }

  private errorsSubscription?: StompSubscription;

  private watchErrors(): void {
    this.errorsSubscription = this.client?.subscribe('/user/queue/errors', (message: IMessage) =>
      this.errorSubject.next(JSON.parse(message.body) as ChatError),
    );
  }
}
