package kr.co.dataric.common.entity.friends;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum FriendStatus {
	PENDING, // 요청중
	ACCEPTED // 수락 완료
}
