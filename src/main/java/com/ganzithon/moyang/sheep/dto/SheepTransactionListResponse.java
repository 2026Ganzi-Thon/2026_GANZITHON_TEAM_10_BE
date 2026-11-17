package com.ganzithon.moyang.sheep.dto;
import java.util.List;
public record SheepTransactionListResponse(List<SheepTransactionResponse> items,Long nextCursor,boolean hasNext) {}
