package kr.co.dataric.kafkaconsumer.repository;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface CustomChatRoomRepository {
	Mono<Void> updateLastMessage(String roomId, String message, LocalDateTime timestamp);
	
	Mono<Long> countUnreadMessages(String roomId, String lastMsgId, LocalDateTime lastReadTime);
}
