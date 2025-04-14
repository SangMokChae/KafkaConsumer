package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatRoomListener {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	
	@KafkaListener(topics = "chat-room-update")
	public void handleChatRoomUpdate(ChatRoomRedisDto dto) {
		try {
			// 1. Redis Key-Value 저장
			String redisKey = "chat_room:" + dto.getRoomId();
			String redisValue = dto.getLastMessage() + "||" + dto.getLastMessageTime();
			redisTemplate.opsForValue()
				.set(redisKey, redisValue)
				.subscribe();
			log.info("✅ Redis에 채팅방 정보 저장 완료: {} = {}", redisKey, redisValue);
			
			// 2. Redis PubSub 전송
			String message = objectMapper.writeValueAsString(dto);
			redisTemplate.convertAndSend("chat-room-update", message).subscribe();
			log.info("📤 Redis PubSub 전송 완료: {}", message);
			
		} catch (Exception e) {
			log.error("❌ Redis PubSub 처리 실패", e);
		}
	}
	
}
