package com.nihil.voice.postcall;
import java.util.Map;
public record CallSummary(String shortSummary,String fullSummary,String intent,String sentiment,String leadQuality,String outcome,String nextAction,Map<String,Object> extractedData) {
 public CallSummary { extractedData=extractedData==null?Map.of():Map.copyOf(extractedData); }
}
