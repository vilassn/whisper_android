package com.whispertflite.kotlin;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Map;

public class Dictionary {
    private final Vocab _vocab;

    private final Map<String, String> _phraseMap;

    public Dictionary(Vocab tokenMappings, Map<String, String> phraseMappings) {
        _vocab = tokenMappings;
        _phraseMap = phraseMappings;
    }

    /**
     * This method takes an int array 2D as an argument and returns a string that is composed of the words of the tokens in the array.
     *
     * @param output 2D int array of tokens
     * @return String composed of words of the tokens in the array
     */
    @NonNull
    public String tokensToString(Collection<Long> output) {
        StringBuilder sb = new StringBuilder();
        for (long token : output) {
            if (token == _vocab.tokenEndOfTranscript) {
                break;
            }
            String word = _vocab.id_to_token.get((int) token);
            if (word != null) {
                Log.i("tokenization", "token: " + token + " word " + word);
                sb.append(word);
            }
        }
        return sb.toString();
    }


    public void logAllTokens() {
        for (int token = 0; token <= 51865; token += 1) {
            String word = _vocab.id_to_token.get(token);
            Log.i("tokenization", "token: " + token + " word " + word);
        }
    }

    /**
     * This method takes a string as an argument and replaces key phrases with special tokens.
     *
     * @param text String to be injected with tokens
     * @return String with injected tokens
     */
    @NonNull
    public String injectTokens(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : _phraseMap.entrySet()) {
            String phrase = entry.getKey();
            String token = entry.getValue();
            result = result.replace(phrase, token);
        }
        return result;
    }

    public int getNotTimeStamps() {
        return _vocab.tokenNoTimeStamps;
    }

    public int getStartOfTranscript() {
        return _vocab.tokenStartOfTranscript;
    }

    public int getEndOfTranscript() {
        return _vocab.tokenEndOfTranscript;
    }
}
