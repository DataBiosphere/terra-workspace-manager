package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;

public record StepRetryRulePair(Step step, RetryRule retryRule) {}
