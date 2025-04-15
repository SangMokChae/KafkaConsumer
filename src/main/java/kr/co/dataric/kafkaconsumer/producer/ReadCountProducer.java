package kr.co.dataric.kafkaconsumer.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.dataric.common.dto.ReadCountMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadCountProducer {
	
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final ObjectMapper objectMapper;
	
	public Mono<Void> send(ReadCountMessage message) {
		try {
			ProducerRecord<String, Object> record = new ProducerRecord<>(
				"chat.read.count", message.getRoomId(), message
			);
			record.headers().add("__TypeId__", "readCountMessage".getBytes(StandardCharsets.UTF_8));
			
			kafkaTemplate.send(record)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("❌ Kafka ReadCount 전송 실패", ex);
					} else {
						log.info("📨 Kafka ReadCount 전송 완료: {}", message);
					}
				});
			
			return Mono.empty(); // Kafka는 블로킹 기반, Mono는 리액티브 흐름 고려용
		} catch (Exception e) {
			log.error("❌ Kafka ReadCount 전송 예외", e);
			return Mono.error(e);
		}
	}
}