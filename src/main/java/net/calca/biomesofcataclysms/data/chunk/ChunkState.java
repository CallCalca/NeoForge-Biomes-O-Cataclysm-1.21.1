package net.calca.biomesofcataclysms.data.chunk;

public enum ChunkState {
    QUEUED, //The chunk is in any sort of queue
    PROCESSING, //The chunk is being processed
    PARTIAL, //The chunk has been partially processed (maybe 1 out of all biomes inside the chunk has been removed). The chunk will be queued in future.
    DONE //The chunk has been completely processed. It will never be queued again.
}