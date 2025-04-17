package kr.co.dataric.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "chat_messages")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
	
	@Id
	private String id;
	private String msgId;
	private String roomId;
	private String sender;
	private String message;
	private LocalDateTime timestamp;
	private boolean edited;
	private boolean deleted;
	
	// ✅ 메시지 타입 기본값 설정 (text, img, video, link 등)
	@Builder.Default
	private String chatType = "text";
}

