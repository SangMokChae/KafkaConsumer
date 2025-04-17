package kr.co.dataric.kafkaconsumer.consumer;

import kr.co.dataric.common.dto.ChatMessageDTO;
import kr.co.dataric.common.entity.ChatMessage;
import kr.co.dataric.kafkaconsumer.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageKafkaListener {
	
	private final ChatMessageRepository chatMessageRepository;
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	
	@KafkaListener(topics = "chat.room.send", groupId = "chat-room-save-group")
	public void listenChatRoomSend(ConsumerRecord<String, ChatMessageDTO> record) {
		ChatMessageDTO msg = record.value();
		log.info("Kafka 수신 - chat.room.send: {}", msg);
		
		// DTO -> Entity
		ChatMessage newChat = new ChatMessage();
		newChat.setMsgId(msg.getId());
		newChat.setRoomId(msg.getRoomId());
		newChat.setSender(msg.getSender());
		newChat.setMessage(msg.getMessage());
		newChat.setTimestamp(msg.getTimestamp());
		newChat.setEdited(false);
		newChat.setDeleted(false);
		
		chatMessageRepository.save(newChat)
			.doOnSuccess(saved -> log.info("✅ MongoDB 채팅 저장 완료: {}", saved))
			.doOnError(e -> log.error("❌ MongoDB 채팅 저장 실패: {}", e.getMessage(), e))
			.onErrorResume(e -> Mono.empty())
			.subscribe();
	}
	
}
