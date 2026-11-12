package com.test.backend.history.application.internal.commandservices;

import com.test.backend.history.domain.model.aggregates.HistoryRecord;
import com.test.backend.history.domain.model.commands.CreateHistoryRecordCommand;
import com.test.backend.history.domain.model.commands.UpdateHistoryRecordCommand;
import com.test.backend.history.domain.model.commands.DeleteHistoryRecordCommand;
import com.test.backend.history.domain.services.HistoryRecordCommandService;
import com.test.backend.history.infrastructure.persistence.jpa.repositories.HistoryRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class HistoryRecordCommandServiceImpl implements HistoryRecordCommandService {

    private final HistoryRecordRepository historyRecordRepository;

    public HistoryRecordCommandServiceImpl(HistoryRecordRepository historyRecordRepository) {
        this.historyRecordRepository = historyRecordRepository;
    }

    @Override
    @Transactional
    public Optional<HistoryRecord> handle(CreateHistoryRecordCommand command) {
        var record = new HistoryRecord(command, command.lab());
        historyRecordRepository.save(record);
        return Optional.of(record);
    }

    @Override
    @Transactional
    public Optional<HistoryRecord> handle(UpdateHistoryRecordCommand command) {
        var recordOpt = historyRecordRepository.findById(command.id());
        if (recordOpt.isEmpty()) return Optional.empty();

        var record = recordOpt.get();
        record.updateFrom(command, command.lab());
        historyRecordRepository.save(record);
        return Optional.of(record);
    }

    @Override
    @Transactional
    public void handle(DeleteHistoryRecordCommand command) {
        historyRecordRepository.deleteById(command.id());
    }
}
