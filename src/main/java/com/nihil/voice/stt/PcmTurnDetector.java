package com.nihil.voice.stt;

/** Lightweight PCM16LE energy VAD used to decide when a Realtime transcription buffer must be committed. */
final class PcmTurnDetector {
    private final int sampleRate;
    private final double rmsThreshold;
    private final long minimumSpeechMs;
    private final long endSilenceMs;
    private final long maximumUtteranceMs;
    private boolean speaking;
    private long speechCandidateMs;
    private long silenceMs;
    private long utteranceMs;
    private boolean speechStarted;

    PcmTurnDetector(int sampleRate,double rmsThreshold,long minimumSpeechMs,long endSilenceMs,long maximumUtteranceMs){
        if(sampleRate<=0||rmsThreshold<0||minimumSpeechMs<=0||endSilenceMs<=0||maximumUtteranceMs<=0)
            throw new IllegalArgumentException("Invalid PCM turn detector configuration");
        this.sampleRate=sampleRate;this.rmsThreshold=rmsThreshold;this.minimumSpeechMs=minimumSpeechMs;
        this.endSilenceMs=endSilenceMs;this.maximumUtteranceMs=maximumUtteranceMs;
    }

    boolean accept(byte[] pcm16le){
        speechStarted=false;
        long frameMs=Math.max(1,(pcm16le.length/2L)*1000L/sampleRate);
        boolean voiced=rms(pcm16le)>=rmsThreshold;
        if(!speaking){
            speechCandidateMs=voiced?speechCandidateMs+frameMs:0;
            if(speechCandidateMs<minimumSpeechMs)return false;
            speaking=true;speechStarted=true;utteranceMs=speechCandidateMs;silenceMs=0;
            return utteranceMs>=maximumUtteranceMs&&commit();
        }
        utteranceMs+=frameMs;
        silenceMs=voiced?0:silenceMs+frameMs;
        if(silenceMs>=endSilenceMs||utteranceMs>=maximumUtteranceMs)return commit();
        return false;
    }

    boolean hasSpeech(){return speaking||speechCandidateMs>=minimumSpeechMs;}
    boolean speechStarted(){return speechStarted;}

    private boolean commit(){reset();return true;}
    private void reset(){speaking=false;speechCandidateMs=0;silenceMs=0;utteranceMs=0;}
    private static double rms(byte[] pcm){
        int samples=pcm.length/2;if(samples==0)return 0;
        long sum=0;
        for(int i=0;i+1<pcm.length;i+=2){int sample=(short)((pcm[i]&0xff)|(pcm[i+1]<<8));sum+=(long)sample*sample;}
        return Math.sqrt((double)sum/samples);
    }
}
