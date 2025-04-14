package kr.co.dataric.kafkaconsumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadEvent {
	private String roomId;
	private String msgId;
	private String userId;
}
