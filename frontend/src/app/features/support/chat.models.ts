/** Types aligned with the back end API responses (package com.yourcaryourway.chat.support.service). */

export type ConversationStatus = 'WAITING' | 'OPEN' | 'CLOSED';

export interface ConversationView {
  id: number;
  status: ConversationStatus;
  clientId: number;
  clientName: string;
  agentId: number | null;
  agentName: string | null;
  reservationId: number | null;
  createdAt: string;
  closedAt: string | null;
}

export interface MessageView {
  id: number;
  conversationId: number;
  authorId: number;
  authorName: string;
  content: string;
  sentAt: string;
}

/** Event broadcast on /topic/conversations.{id}. */
export interface ChatEvent {
  type: 'MESSAGE' | 'TYPING' | 'CLAIMED' | 'CLOSED';
  conversationId: number;
  message: MessageView | null;
  conversation: ConversationView | null;
  actorId: number | null;
  actorName: string | null;
  typing: boolean | null;
}

/** Event broadcast on /topic/support.queue (agents). */
export interface QueueEvent {
  type: 'WAITING' | 'CLAIMED' | 'CLOSED';
  conversation: ConversationView;
}

/** Personal error received on /user/queue/errors. */
export interface ChatError {
  code: string;
  message: string;
}
