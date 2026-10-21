import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConversationView, MessageView } from './chat.models';

const BASE = '/api/v1/support/conversations';

/** Support REST calls. No business rule here: the back end decides, the front end displays. */
@Injectable({ providedIn: 'root' })
export class SupportApiService {
  private readonly http = inject(HttpClient);

  // Customer
  openConversation(reservationId: number | null): Observable<ConversationView> {
    return this.http.post<ConversationView>(BASE, reservationId ? { reservationId } : {});
  }

  getMyConversations(): Observable<ConversationView[]> {
    return this.http.get<ConversationView[]>(BASE);
  }

  // Agent
  getWaitingQueue(): Observable<ConversationView[]> {
    return this.http.get<ConversationView[]>(`${BASE}/queue`);
  }

  getAssignedConversations(): Observable<ConversationView[]> {
    return this.http.get<ConversationView[]>(`${BASE}/assigned`);
  }

  claimConversation(id: number): Observable<ConversationView> {
    return this.http.post<ConversationView>(`${BASE}/${id}/claim`, {});
  }

  closeConversation(id: number): Observable<ConversationView> {
    return this.http.post<ConversationView>(`${BASE}/${id}/close`, {});
  }

  // Participants
  getMessages(id: number, afterId?: number): Observable<MessageView[]> {
    const query = afterId ? `?afterId=${afterId}` : '';
    return this.http.get<MessageView[]>(`${BASE}/${id}/messages${query}`);
  }
}
