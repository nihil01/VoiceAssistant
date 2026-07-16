package com.nihil.voice.crm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice.twenty")
public record TwentyCrmProperties(
        String baseUrl,
        String apiKey,
        String peoplePath,
        String aiCallsPath,
        String callRecordingsPath,
        String voicePromptsPath,
        String notesPath,
        String noteTargetsPath,
        String tasksPath,
        String taskTargetsPath
) {
    public TwentyCrmProperties {
        baseUrl = defaultValue(baseUrl, "http://twenty-server:3000");
        peoplePath = defaultValue(peoplePath, "/rest/people");
        aiCallsPath = defaultValue(aiCallsPath, "/rest/aiCalls");
        callRecordingsPath = defaultValue(
                callRecordingsPath,
                "/rest/callRecordings"
        );
        voicePromptsPath = defaultValue(voicePromptsPath, "/rest/voicePrompts");
        notesPath = defaultValue(notesPath, "/rest/notes");
        noteTargetsPath = defaultValue(noteTargetsPath, "/rest/noteTargets");
        tasksPath = defaultValue(tasksPath, "/rest/tasks");
        taskTargetsPath = defaultValue(taskTargetsPath, "/rest/taskTargets");
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
