package kr.co.dataric.kafkaconsumer.service.read;

import kr.co.dataric.kafkaconsumer.repository.impl.ChatRoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadReceiptService {
	
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final ChatRoomParticipantRepository chatRoomParticipantRepository;
	
	// Redis에 읽은 사용자 추가
	public Mono<Long> saveToRedis(String redisKey, String userId) {
		return redisTemplate.opsForSet().add(redisKey, userId)
			.doOnNext(count -> log.debug("Redis 저장 - {} <- {}", redisKey, userId));
	}
	
	// 읽은 사용자 수 조회
	public Mono<Long> getReadCount(String redisKey) {
		return redisTemplate.opsForSet().size(redisKey);
	}
	
	// 참여자 수 기반 읽지 않은 수 계산
	public Mono<Integer> calculateUnreadCount(String roomId, Object readCount) {
		return chatRoomParticipantRepository.getParticipantCount(roomId)
			.map(total -> Math.max(0, total - (int) readCount));
	}
	
	// 참여자 정보 저장
	public Flux<String> findAllParticipantsByRoomId(String roomId) {
		String key = "chatRoom:participants:"+roomId;
		return redisTemplate.opsForSet().members(key)
			.doOnNext(userId -> log.debug("참여자 조회 - roomId: {}, userI: {}", roomId, userId));
	}
}
