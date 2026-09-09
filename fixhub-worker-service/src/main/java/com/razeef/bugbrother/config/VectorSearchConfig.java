package com.razeef.bugbrother.config;

import com.razeef.bugbrother.grpc.gateway.GatewayGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorSearchConfig {

    private ManagedChannel channel;

    @Bean
    public GatewayGrpc.GatewayBlockingStub gatewayBlockingStub(
            @Value("${vectorsearch.gateway.host:localhost}") String host,
            @Value("${vectorsearch.gateway.port:50053}") int port) {

        this.channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        return GatewayGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
