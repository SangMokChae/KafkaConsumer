package kr.co.dataric.kafkaconsumer.service.read;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomParticipantRepository {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	
	public Mono<Integer> getParticipantCount(String roomId) {
		String key = "chatRoom:participants:"+roomId;
		return redisTemplate.opsForSet().size(key).map(Long::intValue);
	}
}
