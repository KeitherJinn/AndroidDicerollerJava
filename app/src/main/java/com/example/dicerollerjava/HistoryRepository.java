package com.example.dicerollerjava;

import java.io.IOException;
import java.util.List;

public interface HistoryRepository {
    HistoryLoadResult load() throws IOException;
    void save(List<RollBatch> batches) throws IOException;
    void recover(List<RollBatch> batches) throws IOException;
}
