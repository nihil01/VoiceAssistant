package com.nihil.voice.stt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PcmTurnDetectorTest {
    private static byte[] pcm(int samples, short amplitude) {
        byte[] bytes=new byte[samples*2];
        for(int i=0;i<samples;i++){bytes[i*2]=(byte)amplitude;bytes[i*2+1]=(byte)(amplitude>>>8);}
        return bytes;
    }

    @Test void commitsAfterSpeechFollowedBySilence() {
        PcmTurnDetector detector=new PcmTurnDetector(16000,400,60,200,5000);
        byte[] speech=pcm(320,(short)2000); // 20 ms
        byte[] silence=pcm(320,(short)0);
        assertThat(detector.accept(speech)).isFalse();
        assertThat(detector.accept(speech)).isFalse();
        assertThat(detector.accept(speech)).isFalse(); // speech confirmed at 60 ms
        for(int i=0;i<9;i++)assertThat(detector.accept(silence)).isFalse();
        assertThat(detector.accept(silence)).isTrue(); // 200 ms silence
    }

    @Test void doesNotCommitSilenceWithoutSpeech() {
        PcmTurnDetector detector=new PcmTurnDetector(16000,400,40,100,5000);
        byte[] silence=pcm(320,(short)0);
        for(int i=0;i<100;i++)assertThat(detector.accept(silence)).isFalse();
    }

    @Test void commitsLongUtteranceWithoutWaitingForSilence() {
        PcmTurnDetector detector=new PcmTurnDetector(16000,400,40,200,200);
        byte[] speech=pcm(320,(short)2000);
        boolean committed=false;
        for(int i=0;i<20;i++)committed|=detector.accept(speech);
        assertThat(committed).isTrue();
    }
}
