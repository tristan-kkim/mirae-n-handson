package com.example.assignment;

import jakarta.validation.constraints.Size;

/** 재배포 요청 본문. 사유는 선택(최대 200자). */
public record RedistributeRequest(@Size(max = 200) String reason) {
}
