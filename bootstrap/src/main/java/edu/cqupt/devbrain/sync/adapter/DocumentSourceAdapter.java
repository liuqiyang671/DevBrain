package edu.cqupt.devbrain.sync.adapter;

public interface DocumentSourceAdapter {

    String sourceType();

    FetchedContent fetchContent(String sourceLocation) throws Exception;
}
