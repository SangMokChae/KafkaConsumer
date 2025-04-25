package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ReadCountMessage;
import kr.co.dataric.common.dto.ReadReceiptEvent;
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
import java.util.Objects;

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
		String timestamp = String.valueOf(event.getTimestamp());
		String key = READ_KEY_PREFIX + roomId + ":" + userId;
		
		// 1. 마지막 읽은 메시지 ID 갱신
		redisTemplate.opsForValue()
			.set(key, msgId+"_"+timestamp, TTL)
			.doOnSuccess(r -> log.info("✅ 읽음 상태 저장: {} → {}_{}", key, msgId, timestamp))
			.subscribe();
		
		// ✅ 읽지 않은 인원 수 계산 후 Pub/Sub 전송
		calculateUnreadCount(roomId, msgId, timestamp, userId, event.getParticipants())
			.flatMap(readCount -> sendReadCount(roomId, userId, msgId, readCount, event))
			.subscribe();
	}
	
	private boolean isValid(ReadReceiptEvent event) {
		return event != null && event.getRoomId() != null && event.getUserId() != null && event.getMsgId() != null && event.getTimestamp() != null;
	}
	
	private Mono<Integer> calculateUnreadCount(String roomId, String msgId, String msgTimestamp, String sender, List<String> participants) {
		return Flux.fromIterable(participants)
			.filter(uid -> !uid.equals(sender))
			.flatMap(uid -> {
				String key = READ_KEY_PREFIX + roomId +":" +uid;
				return redisTemplate.opsForValue().get(key)
					.filter(Objects::nonNull) // null 방지
					.map(lastRead -> {
						if ("INITIAL".equals(lastRead)) return false;
						String[] parts = lastRead.split("_");
						if (parts.length < 2) return false;
						
						String lastMsgId = parts[0];
						String lastTimestamp = parts[1];
						
						int timeCompare = lastTimestamp.compareTo(msgTimestamp);
						if (timeCompare > 0) return true; // timestamp 최신
						if (timeCompare == 0) {
							return lastMsgId.compareTo(msgId) >= 0;
						}
						return false;
					});
			})
			.filter(isRead -> !isRead)
			.count()
			.map(Long::intValue);
	}
	
	private Mono<Void> sendReadCount(String roomId, String userId, String msgId, Integer readCount, ReadReceiptEvent event) {
		ReadCountMessage dto = ReadCountMessage.builder()
			.roomId(roomId)
			.userId(userId)
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
