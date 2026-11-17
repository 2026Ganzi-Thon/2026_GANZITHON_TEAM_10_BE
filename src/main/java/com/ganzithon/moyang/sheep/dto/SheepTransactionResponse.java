package com.ganzithon.moyang.sheep.dto;
import com.ganzithon.moyang.sheep.domain.SheepTransactionType;
import java.time.OffsetDateTime;
public record SheepTransactionResponse(long transactionId,SheepTransactionType type,int amount,int balanceAfter,String referenceId,Long meetingId,OffsetDateTime createdAt) {}
