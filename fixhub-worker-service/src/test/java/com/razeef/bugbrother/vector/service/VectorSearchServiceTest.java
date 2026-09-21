package com.razeef.bugbrother.vector.service;

import com.razeef.bugbrother.chunking.model.RepositoryChunk;
import com.razeef.bugbrother.grpc.gateway.GatewayGrpc;
import com.razeef.bugbrother.grpc.gateway.GatewayInsertRequest;
import com.razeef.bugbrother.grpc.gateway.GatewayInsertResponse;
import com.razeef.bugbrother.indexing.model.PreparedChunkSubmission;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSearchServiceTest {

    private GatewayGrpc.GatewayBlockingStub stub;
    private VectorSearchService service;

    @BeforeEach
    void setUp() {
        stub = mock(GatewayGrpc.GatewayBlockingStub.class);
        when(stub.withDeadlineAfter(anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(stub);
        service = new VectorSearchService();
        ReflectionTestUtils.setField(service, "gatewayStub", stub);
    }

    @Test
    void retriesTransientInsertWithSameCorrelationId() {
        PreparedChunkSubmission submission = submission();
        when(stub.insert(any(GatewayInsertRequest.class)))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException())
                .thenReturn(GatewayInsertResponse.getDefaultInstance());

        VectorSearchService.ChunkSubmissionResult result =
                service.submitChunks("42", List.of(submission));

        assertEquals(1, result.submittedChunks());
        ArgumentCaptor<GatewayInsertRequest> requests =
                ArgumentCaptor.forClass(GatewayInsertRequest.class);
        verify(stub, times(2)).insert(requests.capture());
        assertEquals(requests.getAllValues().get(0),
                requests.getAllValues().get(1));
        assertEquals(submission.submissionEventId().toString(),
                requests.getValue().getCorrelationId());
    }

    @Test
    void doesNotRetryPermanentInsertFailure() {
        when(stub.insert(any(GatewayInsertRequest.class)))
                .thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

        assertThrows(IllegalStateException.class,
                () -> service.submitChunks("42", List.of(submission())));

        verify(stub, times(1)).insert(any(GatewayInsertRequest.class));
    }

    @Test
    void stopsAfterFourTransientFailures() {
        when(stub.insert(any(GatewayInsertRequest.class)))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThrows(IllegalStateException.class,
                () -> service.submitChunks("42", List.of(submission())));

        verify(stub, times(4)).insert(any(GatewayInsertRequest.class));
    }

    private PreparedChunkSubmission submission() {
        RepositoryChunk chunk = new RepositoryChunk(
                1L, "owner/repo", "commit", "src/A.java", "java", "A",
                1, 2, "file-hash", "chunk-hash", "chunk-id", 7L,
                "line-window-v2", "source", "embedding text"
        );
        return new PreparedChunkSubmission(UUID.randomUUID(), chunk);
    }
}
