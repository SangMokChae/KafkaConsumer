package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomKafkaListener {
	
	private final CustomChatRoomRepository customChatRoomRepository;
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	
	@KafkaListener(topics = "chat.room.redis.update", groupId = "chat-room-group")
	public void onChatRoomUpdate(ConsumerRecord<String, ChatRoomRedisDto> record) {
		ChatRoomRedisDto dto = record.value();
		String roomId = dto.getRoomId();
		String senderId = dto.getLastSender();
		List<String> participants = dto.getParticipants();
		
		if (roomId == null || senderId == null || participants == null) {
			log.warn("❌ Kafka 메시지 누락 또는 참여자 정보 없음 - dto: {}", dto);
			return;
		}
		
		// ✅ 1. MongoDB - 마지막 메시지 업데이트
		customChatRoomRepository.updateLastMessage(
			roomId, dto.getLastMessage(), dto.getLastMessageTime()
		).subscribe();
		
		// ✅ 2. 각 참여자 기준으로 읽지 않은 메시지 수 계산
		Flux<Map.Entry<String, Long>> unreadCountsFlux = Flux.fromIterable(participants)
			.filter(userId -> !userId.equals(senderId)) // 본인 제외
			.flatMap(userId -> {
				String key = "last_read:" + roomId + ":" + userId;
				return redisTemplate.opsForValue().get(key)
					.flatMap(lastRead -> {
						String[] parts = lastRead.split("_");
						if (parts.length < 2) return Mono.just(Map.entry(userId, 1L));
						String lastMsgId = parts[0];
						LocalDateTime lastReadTime = LocalDateTime.parse(parts[1]);
						return customChatRoomRepository.countUnreadMessages(roomId, lastMsgId, lastReadTime)
							.map(count -> Map.entry(userId, count));
					})
					.switchIfEmpty(Mono.just(Map.entry(userId, 1L))); // 읽은 기록이 없으면 기본값 1
			});
		
		// ✅ 3. Redis 저장 및 Pub/Sub 전송
		unreadCountsFlux.collectMap(Map.Entry::getKey, Map.Entry::getValue)
			.flatMap(readCountMap -> {
				dto.setReadCountMap(readCountMap); // ✅ DTO에 추가된 필드: Map<String, Long>
				String redisKey = "chat_room:" + roomId;
				String redisJson;
				try {
					redisJson = objectMapper.writeValueAsString(dto);
				} catch (JsonProcessingException e) {
					log.error("❌ Redis 직렬화 실패", e);
					return Mono.empty();
				}
				
				return redisTemplate.opsForValue()
					.set(redisKey, redisJson, Duration.ofDays(30))
					.flatMap(ok -> {
						if (Boolean.TRUE.equals(ok)) {
							log.info("✅ Redis 저장 성공: {} → {}", redisKey, redisJson);
							return redisTemplate.convertAndSend("chatListUpdate", redisJson);
						}
						log.warn("⚠️ Redis 저장 실패: {}", redisKey);
						return Mono.empty();
					});
			})
			.doOnSuccess(count -> log.info("📨 chatListUpdate 전송 완료 - 수신자 수: {}", count))
			.doOnError(err -> log.error("🔥 처리 실패", err))
			.subscribe();
	}
}