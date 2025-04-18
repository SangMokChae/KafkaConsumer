package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
import kr.co.dataric.common.dto.ReadCountMessage;
import kr.co.dataric.common.dto.ReadReceiptEvent;
import kr.co.dataric.common.entity.ChatRoom;
import kr.co.dataric.kafkaconsumer.repository.ChatRoomRepository;
import kr.co.dataric.kafkaconsumer.repository.CustomChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptConsumer {
	
	private static final Duration TTL = Duration.ofDays(30);
	private static final String READ_KEY_PREFIX = "last_read:";
	private static final String PUBSUB_TOPIC = "chatReadUpdate";
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	
	@KafkaListener(topics = "chat.read.receipt", groupId = "chat-read-receipt-group")
	public void listen(ConsumerRecord<String, ReadReceiptEvent> record) {
		ReadReceiptEvent event = record.value();
		log.info("Kafka 읽음 수신: {}", event);
		
		if (!isValid(event)) {
			log.warn("❌ 유효하지 않은 ReadReceiptEvent 수신됨: {}", event);
			return;
		}
		
		String roomId = event.getRoomId();
		String userId = event.getUserId();
		String msgId = event.getMsgId();
		String key = READ_KEY_PREFIX + roomId + ":" + userId;
		
		// 1. 마지막 읽은 메시지 ID 갱신
		redisTemplate.opsForValue()
			.set(key, msgId, TTL)
			.doOnSuccess(r -> log.info("✅ 읽음 상태 저장: {} → {}", key, msgId))
			.subscribe();
		
		// 2. 참여자 목록 조회 → 읽지 않은 유저 수 계산
		List<String> participants = event.getParticipants();
		if (participants == null || participants.isEmpty()) {
			log.warn("❌ ReadReceiptEvent 내 participants 누락: {}", event);
			return;
		}
		
		// 3. 읽지 않은 인원 계산 후 Pub/Sub 전송
		calculateUnreadCount(roomId, msgId, participants)
			.flatMap(readCount -> sendReadCount(roomId, msgId, readCount, event))
			.subscribe();
	}
	
	private boolean isValid(ReadReceiptEvent event) {
		return event != null && event.getRoomId() != null && event.getUserId() != null && event.getMsgId() != null;
	}
	
	private Mono<Integer> calculateUnreadCount(String roomId, String msgId, List<String> participants) {
		return Flux.fromIterable(participants)
			.flatMap(userId -> {
				String key = "last_read:" + roomId + ":" + userId;
				return redisTemplate.opsForValue().get(key)
					.defaultIfEmpty("INITIAL")
					.map(lastReadMsgId -> lastReadMsgId.compareTo(msgId) >= 0); // true면 읽음
			})
			.filter(read -> !read)
			.count()
			.map(Long::intValue);
	}
	
	private Mono<Void> sendReadCount(String roomId, String msgId, Integer readCount, ReadReceiptEvent event) {
		ReadCountMessage dto = ReadCountMessage.builder()
			.roomId(roomId)
			.msgId(msgId)
			.readCount(readCount)
			.lastMessageTime(event.getTimestamp())
			.sender(event.getUserId())
			.build();
		
		try {
			String json = objectMapper.writeValueAsString(dto);
			return redisTemplate.convertAndSend(PUBSUB_TOPIC, json)
				.doOnSuccess(i -> log.info("읽음 수 Pub/Sub 전송 완료 - {}", dto))
				.doOnError(e -> log.error("Pub/Sub 전송 실패", e))
				.then();
		} catch (Exception e) {
			log.error("DTO 직렬화 실패 - {}", dto, e);
			return Mono.empty();
		}
	}
}
