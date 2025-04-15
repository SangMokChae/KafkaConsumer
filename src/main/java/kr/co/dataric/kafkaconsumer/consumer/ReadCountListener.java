package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
import kr.co.dataric.common.dto.ReadCountMessage;
import kr.co.dataric.kafkaconsumer.config.redis.RedisScanHelper;
import kr.co.dataric.kafkaconsumer.sink.UserSinkManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadCountListener {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	private final UserSinkManager userSinkManager;
	private final RedisScanHelper scanHelper;
	
	@KafkaListener(topics = "chat.read.count", groupId = "chat-read-group")
	public void handle(ReadCountMessage message) {
		int readCount = message.getReadCount();
		if (readCount <= 0) {
			log.info("ℹ️ readCount == 0 → Sink 전파 생략 - roomId: {}, msgId: {}", message.getRoomId(), message.getMsgId());
			return;
		}
		
		String roomId = message.getRoomId();
		
		String participantsKey = "chatRoom:participants:"+roomId;
		
		// 참여자별 Sink로 읽음 수 반영 메시지 전송
		redisTemplate.opsForSet().members(participantsKey)
			.filter(Objects::nonNull)
			.flatMap(userId -> {
				String keyPattern = "chat_room:" +roomId +":" +userId +":";
				return scanHelper.scanKeys(keyPattern)
					.flatMap(key -> redisTemplate.opsForValue().get(key)
						.flatMap(json -> {
							try {
								ChatRoomRedisDto dto = objectMapper.readValue(json, ChatRoomRedisDto.class);
								dto.setReadCount(readCount);
								
								Set<Sinks.Many<ChatRoomRedisDto>> sinks = userSinkManager.get(userId);
								if (sinks != null) {
									sinks.forEach(sink -> sink.tryEmitNext(dto));
									log.info("WebSocket 전송 - userId: {}, roomId: {}", userId, roomId);
								}
							} catch (Exception e) {
								log.error("Redis DTO 역직렬화 실패 - userId: {}, key: {}", userId, key);
							}
							return Mono.empty();
					}));
			})
			.subscribe();
	}
	
}
