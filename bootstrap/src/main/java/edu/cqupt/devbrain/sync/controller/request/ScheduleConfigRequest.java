package edu.cqupt.devbrain.sync.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleConfigRequest(
        @NotBlank @Size(max = 32) String sourceType,
        @Size(max = 512) String sourceLocation,
        @NotNull Integer scheduleEnabled,
        @Size(max = 64) String scheduleCron
) {
}
