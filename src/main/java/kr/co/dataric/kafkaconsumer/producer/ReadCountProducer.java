package kr.co.dataric.kafkaconsumer.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadCountProducer {
	
	private final ReactiveKafkaProducerTemplate<String, String> kafkaProducer;
	private final ObjectMapper objectMapper;
	
	public Mono<Void> send(String roomId, String msgId, int readCount) {
		try {
			Map<String, Object> payload = Map.of(
				"roomId", roomId,
				"msgId", msgId,
				"readCount", readCount
			);
			
			String json = objectMapper.writeValueAsString(payload);
			
			return kafkaProducer.send("chat.read.count", roomId, json)
				.doOnSuccess(result -> log.info("Kafka ReadCount 전송 완료 : {}", json))
				.then();
		} catch (Exception e) {
			log.error("Kafka ReadCount 전송 실패", e);
			return Mono.error(e);
		}
	}
}
