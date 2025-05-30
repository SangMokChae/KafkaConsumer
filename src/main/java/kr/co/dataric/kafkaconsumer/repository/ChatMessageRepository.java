package kr.co.dataric.kafkaconsumer.repository;

import kr.co.dataric.common.entity.ChatMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
}
