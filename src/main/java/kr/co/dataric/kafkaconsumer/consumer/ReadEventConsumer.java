package kr.co.dataric.kafkaconsumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.kafkaconsumer.dto.ReadEvent;
import kr.co.dataric.kafkaconsumer.producer.ReadCountProducer;
import kr.co.dataric.kafkaconsumer.service.read.ReadReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadEventConsumer {

	private final ReadReceiptService readReceiptService;
	private final ReadCountProducer readCountProducer;
	private final ObjectMapper objectMapper;
	
	@KafkaListener(topics = "chat.read", groupId = "chat-read-group")
	public void handle(ReadEvent event) {
		String redisKey = "read:" + event.getMsgId();
		
		readReceiptService.saveToRedis(redisKey, event.getUserId())
			.then(readReceiptService.getReadCount(redisKey))
			.flatMap(readCount -> readReceiptService.calculateUnreadCount(event.getRoomId(), readCount))
			.flatMap(unread -> readCountProducer.send(event.getRoomId(), event.getMsgId(), unread))
			.subscribe();
	}

}
