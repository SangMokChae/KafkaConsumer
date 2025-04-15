package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class ChatRoomRedisSubscriber {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	
	@KafkaListener(topics = "chat.room.redis.update", groupId = "chat-room-group")
	public void onChatRoomUpdate(String json) throws JsonProcessingException {
		ChatRoomRedisDto dto = objectMapper.readValue(json, ChatRoomRedisDto.class);
		
		// Redis 저장
		redisTemplate.opsForValue().set(dto.getRedisKey(), json);
		
		// Redis Pub/Sub
		redisTemplate.convertAndSend("chat-room-update", json);
		
		log.info("📨 Kafka 수신 → Redis 저장 및 PubSub 완료: {}", dto);
	}
	
}
