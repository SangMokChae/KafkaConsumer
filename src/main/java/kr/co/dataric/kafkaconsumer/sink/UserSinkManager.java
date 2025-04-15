package kr.co.dataric.kafkaconsumer.sink;

import kr.co.dataric.common.dto.ChatRoomRedisDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSinkManager {
	
	private final Map<String, Set<Sinks.Many<ChatRoomRedisDto>>> userSinks = new ConcurrentHashMap<>();
	
	/**
	 * 유저별 Sink 등록 (다중 브라우저/디바이스 지원)
	 */
	public void register(String userId, Sinks.Many<ChatRoomRedisDto> sink) {
		userSinks.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(sink);
		log.info("✅ Sink 등록 - userId: {} (총 {}개)", userId, userSinks.get(userId).size());
	}
	
	/**
	 * 유저별 Sink 제거 (WebSocket 종료 시)
	 */
	public void remove(String userId, Sinks.Many<ChatRoomRedisDto> sink) {
		Set<Sinks.Many<ChatRoomRedisDto>> sinks = userSinks.get(userId);
		if (sinks != null) {
			sinks.remove(sink);
			if (sinks.isEmpty()) {
				userSinks.remove(userId);
			}
			log.info("❎ Sink 제거 - userId: {}, 남은 연결 수: {}", userId, sinks.size());
		}
	}
	
	/**
	 * 특정 유저에게 메시지 전송 (다중 Sink 대응)
	 */
	public void emitToUser(String userId, ChatRoomRedisDto dto) {
		Set<Sinks.Many<ChatRoomRedisDto>> sinks = userSinks.get(userId);
		if (sinks != null) {
			sinks.forEach(sink -> sink.tryEmitNext(dto));
			log.info("📤 {} 유저에게 채팅방 업데이트 전파", userId);
		}
	}
	
	/**
	 * 채팅방 참여자 전체에게 메시지 전파
	 */
	public void emitToRoom(String roomId, ChatRoomRedisDto dto) {
	}
	
	/**
	 * 전체 사용자에게 메시지 브로드캐스트 (공지 등)
	 */
	public void emitToAll(ChatRoomRedisDto dto) {
		userSinks.values().forEach(sinkSet -> {
			for (Sinks.Many<ChatRoomRedisDto> sink : sinkSet) {
				sink.tryEmitNext(dto);
			}
		});
		log.info("📢 전체 사용자에게 메시지 전파");
	}
	
	public boolean exists(String userId) {
		return userSinks.containsKey(userId);
	}
	
	public int size() {
		return userSinks.size();
	}
	
	public Set<Sinks.Many<ChatRoomRedisDto>> get(String userId) {
		return userSinks.getOrDefault(userId, Set.of());
	}
}
