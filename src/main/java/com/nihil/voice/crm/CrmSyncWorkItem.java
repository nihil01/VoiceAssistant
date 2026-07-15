package com.nihil.voice.crm;

import java.util.UUID;

public record CrmSyncWorkItem(UUID jobId,int attempts,CrmCallData call) {}
