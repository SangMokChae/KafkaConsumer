package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ReadReceiptEvent;
import kr.co.dataric.common.service.RedisService;
import kr.co.dataric.kafkaconsumer.repository.ChatMessageRepository;
import kr.co.dataric.kafkaconsumer.repository.impl.CustomChatRoomRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptConsumer {
	
	private final RedisService redisService;
	private final ObjectMapper objectMapper;
	private final ChatMessageRepository chatMessageRepository;
	private final CustomChatRoomRepositoryImpl customChatRoomRepository;
	
	@KafkaListener(topics = "read_receipt", groupId = "chat-read-sync")
	public void handleReadReceipt(ReadReceiptEvent event) {
		try {
			String redisKey = "read:" +event.getMsgId();
			
			// 읽은 사용자 Redis에 저장 + TTL 10Min
			redisService.addToSet(redisKey, event.getUserId());
		} catch (Exception e) {
			log.error("읽음 Kafka 처리 실패", e);
		}
	}
	
	@Scheduled(fixedRate = 10000) // 10초 마다 실행
	public void syncReadCount() {
		redisService.scanKeys("read:*")
			.flatMap(key -> {
				String msgId = key.replace("read:", "");
				return redisService.getSetSize(key)
					.flatMap(readCount -> customChatRoomRepository.updateReadCountByMsgId(msgId, readCount))
					.then(redisService.deleteKey(key));
			})
			.doOnCancel(() -> log.debug("✅ 모든 읽음 상태 동기화 완료"))
			.subscribe();
	}
	
}