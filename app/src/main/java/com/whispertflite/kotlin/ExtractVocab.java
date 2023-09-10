package com.whispertflite.kotlin;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ExtractVocab {

    public static Vocab extractVocab(InputStream filtersVocabBin) throws IOException {
        if (readI32(filtersVocabBin) == 0x5553454e) {


            readI32(filtersVocabBin);
            readI32(filtersVocabBin);
            readVecF32(filtersVocabBin, 80 * 201);

            Vocab vocab = new Vocab();
            int word_count = readI32(filtersVocabBin);
            assert (50257 == word_count);
            HashMap<Integer, String> words = new HashMap<>(word_count);
            for (int i = 0; i < word_count; i++) {
                int nextWordLen = readU32(filtersVocabBin);
                String word = readString(filtersVocabBin, nextWordLen);
                words.put(i, word);
            }
            vocab.n_vocab = word_count;
            vocab.id_to_token = words;
            if (true) {
//            if (vocab.isMultilingual()) {

                Log.i("extractVocab", "Using Multilingual");
                vocab.tokenEndOfTranscript += 1;
                vocab.tokenStartOfTranscript += 1;
                vocab.token_prev += 1;
                vocab.token_solm += 1;
                vocab.tokenNoTimeStamps += 1;
                vocab.token_beg += 1;
            }
/*            for (int i = word_count; i < vocab.n_vocab; i++) {
                String word;
                if (i > vocab.token_beg) {
                    word = "[_TT_" + (i - vocab.token_beg) + "]";
                } else if (i == vocab.token_eot) {
                    word = "[_EOT_]";
                } else if (i == vocab.token_sot) {
                    word = "[_SOT_]";
                } else if (i == vocab.token_prev) {
                    word = "[_PREV_]";
                } else if (i == vocab.token_not) {
                    word = "[_NOT_]";
                } else if (i == vocab.token_beg) {
                    word = "[_BEG_]";
                } else {
                    word = "[_extra_token_" + i + "]";
                }
                vocab.id_to_token.put(i, word);
            }*/
            System.out.println("Succeeded in Loading Vocab! " + vocab.n_vocab + " (" + vocab.id_to_token.size() + ") Words.");
            return vocab;
        } else throw new IOException("bad magic");
    }

    private static List<Float> readVecF32(InputStream asset, int numberOfBytes) throws IOException {
        byte[] data = new byte[4 * numberOfBytes];
        asset.read(data);

        List<Float> vec = new LinkedList<Float>();
        for (int i = 0; i < data.length; i += 4) {
            byte[] chunk = new byte[4];
            System.arraycopy(data, i, chunk, 0, 4);
            float f = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            vec.add(f);
        }
        return vec;
    }

    private static String readString(InputStream asset, int stringLen) throws IOException {
        byte[] data = new byte[stringLen];
        asset.read(data);

        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append((char) b);
        }
        return sb.toString();
    }

    private static int readI32(InputStream asset) throws IOException {
        byte[] buffer = new byte[4];
        asset.read(buffer);
        int anInt = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return anInt;
    }

    private static int readU32(InputStream asset) throws IOException {
        byte[] buffer = new byte[4];
        asset.read(buffer);
        int anInt = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return anInt;
    }

}
