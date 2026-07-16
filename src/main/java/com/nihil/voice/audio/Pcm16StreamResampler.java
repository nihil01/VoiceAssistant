package com.nihil.voice.audio;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Stateful mono PCM16LE resampler that preserves sample and interpolation state across transport chunks. */
public final class Pcm16StreamResampler {
    private final int sourceRate;
    private final int targetRate;
    private final double sourceStep;
    private final List<Short> samples=new ArrayList<>();
    private long baseInputIndex;
    private long totalInputSamples;
    private long emittedOutputSamples;
    private int pendingLowByte=-1;
    private boolean finished;

    public Pcm16StreamResampler(int sourceRate,int targetRate){
        if(sourceRate<=0||targetRate<=0)throw new IllegalArgumentException("Sample rates must be positive");
        this.sourceRate=sourceRate;this.targetRate=targetRate;this.sourceStep=(double)sourceRate/targetRate;
    }

    public synchronized byte[] append(byte[] chunk){
        if(finished)throw new IllegalStateException("PCM stream is already finished");
        if(chunk==null||chunk.length==0)return new byte[0];
        int offset=0;
        if(pendingLowByte>=0){
            addSample((short)(pendingLowByte|((chunk[0]&0xff)<<8)));
            pendingLowByte=-1;offset=1;
        }
        while(offset+1<chunk.length){
            addSample((short)((chunk[offset]&0xff)|((chunk[offset+1]&0xff)<<8)));
            offset+=2;
        }
        if(offset<chunk.length)pendingLowByte=chunk[offset]&0xff;
        return emit(false);
    }

    public synchronized byte[] finish(){
        if(finished)return new byte[0];
        finished=true;
        if(pendingLowByte>=0)throw new IllegalStateException("PCM stream ended with an incomplete PCM16 sample");
        return emit(true);
    }

    private void addSample(short sample){samples.add(sample);totalInputSamples++;}

    private byte[] emit(boolean flush){
        if(samples.isEmpty())return new byte[0];
        long finalOutputCount=flush?Math.max(1,Math.round(totalInputSamples*(double)targetRate/sourceRate)):Long.MAX_VALUE;
        var output=new ByteArrayOutputStream();
        long lastInputIndex=baseInputIndex+samples.size()-1L;
        while(emittedOutputSamples<finalOutputCount){
            double position=emittedOutputSamples*sourceStep;
            long leftIndex=(long)Math.floor(position);
            long rightIndex=leftIndex+1;
            if(!flush&&rightIndex>lastInputIndex)break;
            if(leftIndex>lastInputIndex)leftIndex=lastInputIndex;
            if(rightIndex>lastInputIndex)rightIndex=lastInputIndex;
            short left=samples.get(Math.toIntExact(leftIndex-baseInputIndex));
            short right=samples.get(Math.toIntExact(rightIndex-baseInputIndex));
            double fraction=position-Math.floor(position);
            int value=(int)Math.round(left+(right-left)*fraction);
            short result=(short)Math.clamp(value,Short.MIN_VALUE,Short.MAX_VALUE);
            output.write(result&0xff);output.write((result>>>8)&0xff);
            emittedOutputSamples++;
        }
        long nextNeeded=(long)Math.floor(emittedOutputSamples*sourceStep);
        int removable=(int)Math.min(samples.size(),Math.max(0,nextNeeded-baseInputIndex));
        if(removable>0){samples.subList(0,removable).clear();baseInputIndex+=removable;}
        return output.toByteArray();
    }
}
