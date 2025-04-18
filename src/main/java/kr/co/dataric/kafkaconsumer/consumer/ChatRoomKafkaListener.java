package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import kr.co.dataric.common.dto.ChatMessageDTO;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
import kr.co.dataric.kafkaconsumer.repository.ChatMessageRepository;
import kr.co.dataric.kafkaconsumer.repository.CustomChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

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
		
		// 1. Mongo - chat_room 컬렉션의 마지막 메시지 업데이트
		customChatRoomRepository.updateLastMessage(dto.getRoomId(), dto.getLastMessage(), dto.getLastMessageTime()).subscribe();
		
		// 2. Redis - 최신 메시지 캐싱 ("chat_room:{roomId}" : "message||timestamp")
		String redisJson;
		String redisKey = "chat_room:" + dto.getRoomId();
		try {
			redisJson = objectMapper.writeValueAsString(dto);
		} catch (Exception e) {
			log.error("Redis 저장용 JSON 직렬화 실패");
			return;
		}
		
		Mono<Boolean> saveToRedis = redisTemplate.opsForValue()
			.set(redisKey, redisJson, Duration.ofDays(30));
		
		// 3. Redis Pub/Sub - WebSocket 서버에 Sink 알림
		Mono<Long> publishToRedis = redisTemplate.convertAndSend("chatListUpdate", redisJson);
		
		// 실행 순서: Redis 저장 성공 후 -> WebSocket 알림
		saveToRedis
			.flatMap(success -> {
				if (Boolean.TRUE.equals(success)) {
					log.info("Redis 저장 성공 : {} -> {}", redisKey, redisJson);
					return publishToRedis;
				} else {
					log.warn("Redis 저장 실패: {}", redisKey);
					return Mono.empty();
				}
			})
			.doOnSuccess(count -> log.info("Redis Pub/Sub 전송 완료: {} 명 수신", count))
			.doOnError(error -> log.error("Redis Pub/Sub 전송 실패", error))
			.subscribe();
	}
}
