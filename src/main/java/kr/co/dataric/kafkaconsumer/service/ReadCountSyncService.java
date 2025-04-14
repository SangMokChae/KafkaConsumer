package kr.co.dataric.kafkaconsumer.service;

import kr.co.dataric.common.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadCountSyncService {
	
	private final ReactiveMongoTemplate mongoTemplate;
	
	public Mono<Void> updateReadCountByMsgId(String msgId, long readCount) {
		Query query= Query.query(Criteria.where("msgId").is(msgId));
		Update update = new Update().set("readCount", readCount);
		
		return mongoTemplate.updateFirst(query, update, ChatMessage.class)
			.doOnSuccess(result -> log.debug("Mongo readCount 업데이트 - msgId: {}, count: {}", msgId, readCount))
			.then();
	}
	
}
