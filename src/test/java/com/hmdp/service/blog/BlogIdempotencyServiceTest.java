package com.hmdp.service.blog;

import com.hmdp.entity.IdempotencyRecord;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.IdempotencyRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogIdempotencyServiceTest {

    @Mock
    private IdempotencyRecordMapper mapper;

    @InjectMocks
    private BlogIdempotencyService service;

    @Test
    void begin_should_identify_record_created_by_current_transaction() {
        AtomicReference<IdempotencyRecord> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            record.setId(5L);
            inserted.set(record);
            return 1;
        }).when(mapper).insertOrGetId(any(IdempotencyRecord.class));
        when(mapper.selectByIdForUpdate(5L)).thenAnswer(invocation -> inserted.get());

        IdempotencyDecision decision = service.begin(7L, "request-key", "hash-a");

        assertFalse(decision.shouldUsePreviousResult());
        assertEquals(5L, decision.getRecordId());
        assertEquals(inserted.get().getOwnerToken(), decision.getOwnerToken());
    }

    @Test
    void begin_should_return_previous_result_even_if_resource_was_deleted() {
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            record.setId(5L);
            return 0;
        }).when(mapper).insertOrGetId(any(IdempotencyRecord.class));
        when(mapper.selectByIdForUpdate(5L)).thenReturn(new IdempotencyRecord()
                .setId(5L)
                .setRequestHash("hash-a")
                .setStatus(IdempotencyRecord.STATUS_SUCCEEDED)
                .setResourceId(99L)
                .setOwnerToken("first-request"));

        IdempotencyDecision decision = service.begin(7L, "request-key", "hash-a");

        assertTrue(decision.shouldUsePreviousResult());
        assertEquals(99L, decision.getResourceId());
    }

    @Test
    void begin_should_reject_same_key_with_different_payload() {
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            record.setId(5L);
            return 0;
        }).when(mapper).insertOrGetId(any(IdempotencyRecord.class));
        when(mapper.selectByIdForUpdate(5L)).thenReturn(new IdempotencyRecord()
                .setId(5L)
                .setRequestHash("hash-first")
                .setStatus(IdempotencyRecord.STATUS_SUCCEEDED)
                .setResourceId(99L));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.begin(7L, "request-key", "hash-second"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getCode());
    }

    @Test
    void complete_should_persist_first_response_snapshot() {
        when(mapper.markSucceeded(5L, "owner", 99L, "99")).thenReturn(1);

        service.complete(IdempotencyDecision.createBlog(5L, "owner"), 99L);

        verify(mapper).markSucceeded(5L, "owner", 99L, "99");
    }
}
