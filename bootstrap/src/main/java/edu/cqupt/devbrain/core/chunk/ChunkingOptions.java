package edu.cqupt.devbrain.core.chunk;

/**
 * 分块配置的统一入口类型，仅允许固定长度配置和文本边界配置两种实现。
 */
public sealed interface ChunkingOptions permits FixedSizeOptions, TextBoundaryOptions {
}
