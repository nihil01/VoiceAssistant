package com.nihil.voice.crm;

@org.springframework.boot.context.properties.ConfigurationProperties("voice.twenty")
public record TwentyCrmProperties(String baseUrl, String apiKey, String peoplePath, String aiCallsPath,
                                  String notesPath, String tasksPath) {
    public TwentyCrmProperties {
        peoplePath = defaultPath(peoplePath, "/rest/people");
        aiCallsPath = defaultPath(aiCallsPath, "/rest/aiCalls");
        notesPath = defaultPath(notesPath, "/rest/notes");
        tasksPath = defaultPath(tasksPath, "/rest/tasks");
    }
    private static String defaultPath(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
