package com.test.backend.automation.application.internal.commandservices;

import com.test.backend.automation.domain.model.aggregates.AutomationRule;
import com.test.backend.automation.domain.model.commands.CreateAutomationRuleCommand;
import com.test.backend.automation.domain.model.commands.UpdateAutomationRuleCommand;
import com.test.backend.automation.domain.model.commands.DeleteAutomationRuleCommand;
import com.test.backend.automation.domain.services.AutomationRuleCommandService;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.AutomationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AutomationRuleCommandServiceImpl implements AutomationRuleCommandService {

    private final AutomationRuleRepository automationRuleRepository;

    public AutomationRuleCommandServiceImpl(AutomationRuleRepository automationRuleRepository) {
        this.automationRuleRepository = automationRuleRepository;
    }

    @Override
    @Transactional
    public Optional<AutomationRule> handle(CreateAutomationRuleCommand command) {
        var rule = new AutomationRule(command, command.specificLabId(), null);
        automationRuleRepository.save(rule);
        return Optional.of(rule);
    }

    @Override
    @Transactional
    public Optional<AutomationRule> handle(UpdateAutomationRuleCommand command) {
        var ruleOpt = automationRuleRepository.findById(command.id());
        if (ruleOpt.isEmpty()) return Optional.empty();

        var rule = ruleOpt.get();
        rule.updateFrom(command, command.specificLabId());
        automationRuleRepository.save(rule);
        return Optional.of(rule);
    }

    @Override
    @Transactional
    public void handle(DeleteAutomationRuleCommand command) {
        automationRuleRepository.deleteById(command.id());
    }
}
