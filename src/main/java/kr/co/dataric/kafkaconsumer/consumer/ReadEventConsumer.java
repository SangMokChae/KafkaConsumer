package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ChatRoomRedisDto;
import kr.co.dataric.common.dto.ReadCountMessage;
import kr.co.dataric.common.dto.ReadReceiptEvent;
import kr.co.dataric.kafkaconsumer.config.redis.RedisScanHelper;
import kr.co.dataric.kafkaconsumer.producer.ReadCountProducer;
import kr.co.dataric.kafkaconsumer.service.read.ReadReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadEventConsumer {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ReadCountProducer readCountProducer;
	private final ReadReceiptService readReceiptService;
	private final ObjectMapper objectMapper;
	private final RedisScanHelper scanHelper;
	
	@KafkaListener(topics = "chat.read", groupId = "chat-read-group")
	public void handleRead(ReadReceiptEvent event) {
		
		log.info("event :: {}", event);
		
		String msgId = event.getMsgId();
		String roomId = event.getRoomId();
		String userId = event.getUserId();
		
		String readKey = "read:" + msgId;
		
		// 1️⃣ 읽은 유저 Redis에 등록 + 참여자 수 계산
		redisTemplate.opsForSet().add(readKey, userId)
			.then(redisTemplate.opsForSet().size(readKey))
			.zipWith(readReceiptService.findAllParticipantsByRoomId(roomId).collectList())
			.flatMap(tuple -> {
				Long readCount = tuple.getT1();
				List<String> participants = tuple.getT2();
				
				int unread = Math.max(0, participants.size() - readCount.intValue());
				
				// 2️⃣ chat_room:{roomId}:* 키 중 가장 최신 DTO 찾기
				String keyPattern = "chat_room:" + roomId + ":*";
				
				return scanHelper.scanKeys(keyPattern)
					.flatMap(key ->
						redisTemplate.opsForValue().get(key)
							.map(json -> {
								try {
									return objectMapper.readValue(json, ChatRoomRedisDto.class);
								} catch (Exception e) {
									log.warn("❌ Redis DTO 역직렬화 실패 - key: {}", key);
									return null;
								}
							})
					)
					.filter(Objects::nonNull)
					.sort((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()))
					.next()
					.map(dto -> ReadCountMessage.builder()
						.roomId(roomId)
						.msgId(msgId)
						.readCount(unread)
						.lastMessage(dto.getLastMessage())
						.lastMessageTime(dto.getLastMessageTime())
						.build());
			})
			.flatMap(readCountProducer::send)
			.doOnError(e -> log.error("❌ 읽음 처리 전체 실패", e))
			.subscribe();
	}
}
