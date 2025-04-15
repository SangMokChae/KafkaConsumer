package kr.co.dataric.kafkaconsumer.config.redis;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RedisScanHelper {
	
	private final ReactiveRedisConnectionFactory connectionFactory;

	public Flux<String> scanKeys(String pattern) {
		return Flux.usingWhen(
			Mono.fromCallable(connectionFactory::getReactiveConnection),
			connection -> {
				ReactiveKeyCommands keyCommands = connection.keyCommands();
				
				ScanOptions options = ScanOptions.scanOptions()
					.match(pattern)
					.count(100)
					.build();
				
				return keyCommands.scan(options).map(this::decodeKey);
			},
			connection -> Mono.fromRunnable(connection::close) // 안전하게 close
		);
	}
	
	private String decodeKey(ByteBuffer buffer) {
		return StandardCharsets.UTF_8.decode(buffer).toString();
	}
}