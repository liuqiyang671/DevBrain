package edu.cqupt.devbrain.infra.ai.gateway.structured;

public interface AiStructuredGateway {

    <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request);
}
