package kr.co.dataric.kafkaconsumer.repository;

import kr.co.dataric.common.entity.ChatRoom;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ChatRoomRepository extends ReactiveMongoRepository<ChatRoom, String> {
}
