package com.whispertflite.kotlin;

import java.util.HashMap;

public class Vocab {

    public int n_vocab;
    public int tokenEndOfTranscript;
    public int tokenStartOfTranscript;
    public int token_prev;
    public int token_solm;
    public int tokenNoTimeStamps;
    public int token_beg;
    public HashMap<Integer, String> id_to_token;

    public static final int TRANSLATE = 50358;
    public static final int TRANSCRIBE = 50359;

    public Vocab() {
        // Magic Numbers evidently derived from https://github.com/ggerganov/whisper.cpp
        this.n_vocab = 51864;
        this.tokenEndOfTranscript = 50256;
        this.tokenStartOfTranscript = 50257;
        this.token_prev = 50360;
        this.token_solm = 50361;
        this.tokenNoTimeStamps = 50362;
        this.token_beg = 50363;
        this.id_to_token = new HashMap<Integer, String>();
    }

    public boolean isMultilingual() {
        return this.n_vocab == 51865;
    }

}
