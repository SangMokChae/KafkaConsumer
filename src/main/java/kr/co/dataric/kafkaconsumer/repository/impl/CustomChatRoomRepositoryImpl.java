package kr.co.dataric.kafkaconsumer.repository.impl;

import kr.co.dataric.common.entity.ChatMessage;
import kr.co.dataric.kafkaconsumer.repository.CustomChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomChatRoomRepositoryImpl implements CustomChatRoomRepository {
	
	private final ReactiveMongoTemplate mongoTemplate;
	
	public Mono<Void> updateReadCountByMsgId(String msgId, Long readCount) {
		Query query = Query.query(Criteria.where("msgId").is(msgId));
		Update update = new Update().set("readCount", readCount);
		return mongoTemplate.updateFirst(query, update, ChatMessage.class).then();
	}
	
}
