/*  AmharicAudioBibleTextGridReader.java

    Copyright 2009-2014 Andrew Rosenberg
    Added by Yishak Tofik Mohammed

    This file is part of the AuToBI prosodic analysis package.

    AuToBI is free software: you can redistribute it and/or modify
    it under the terms of the Apache License (see boilerplate below)

 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You should have received a copy of the Apache 2.0 License along with AuToBI.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 ***********************************************************************************************************************
 */
package edu.cuny.qc.speech.AuToBI.io;

import edu.cuny.qc.speech.AuToBI.core.AuToBIException;
import edu.cuny.qc.speech.AuToBI.core.Region;
import edu.cuny.qc.speech.AuToBI.core.Word;
import edu.cuny.qc.speech.AuToBI.util.AlignmentUtils;
import edu.cuny.qc.speech.AuToBI.util.AuToBIUtils;
import edu.cuny.qc.speech.AuToBI.util.ToBIUtils;
import edu.cuny.qc.speech.AuToBI.util.WordReaderUtils;

import java.util.List;
import java.util.ArrayList;
import java.io.*;

/**
 * Read a TextGrid and generate a list of Words.
 * <p/>
 * The names of orthogonal, tones and breaks tiers in the TextGrid can be specified or standard "words", "tones",
 * "breaks" can be used.
 */
public class AmharicAudioBibleTextGridReader extends TextGridReader {

    protected String filename;          // the name of the textgrid file
    protected String charsetName;  // the name of the character set of the file to read.

    protected String words_tier_name;   // the name of the words tier
    protected String phones_tier_name;   // the name of the phones tier
    protected String breaks_tier_name;  // the name of the breaks tier

    protected Tier words_tier;   // a words Tier object
    protected Tier phones_tier;   // a phones Tier object
    protected Tier breaks_tier;  // a breaks Tier object

    /**
     * Constructs a new TextGridReader for a TextGrid file with default tier names.
     *
     * @param filename the filename to read
     */
    public AmharicAudioBibleTextGridReader(String filename) {
    	super(filename);
        this.filename = filename;
    }

    /**
     * Constructs a new TextGridReader for a TextGrid file with default tier names.
     *
     * @param filename    the filename to read
     * @param charsetName the name of the character set for the input
     */
    

    /**
     * Constructs a new TextGridReader with specified file and tier names.
     *
     * @param filename         the file name
     * @param words_tier_name  the name of the orthogonal tier
     * @param tones_tier_name  the name of the tones tier
     * @param breaks_tier_name the name of the breaks tier
     */
    public AmharicAudioBibleTextGridReader(String filename, String words_tier_name, String phones_tier_name, String breaks_tier_name) {
    	super(filename, null, null, breaks_tier_name);
    	this.filename = filename;
        this.words_tier_name = words_tier_name;
        this.phones_tier_name = phones_tier_name;
        this.breaks_tier_name = breaks_tier_name;
    }

    /**
     * Constructs a new TextGridReader with specified file and tier names.
     *
     * @param filename         the file name
     * @param words_tier_name  the name of the orthogonal tier
     * @param tones_tier_name  the name of the tones tier
     * @param breaks_tier_name the name of the breaks tier
     * @param charsetName      the name of the character set for the input
     */
    public AmharicAudioBibleTextGridReader(String filename, String words_tier_name, String phones_tier_name, String breaks_tier_name,
                          String charsetName) {
    	super(filename, null, null, breaks_tier_name, charsetName);
        this.filename = filename;
        this.words_tier_name = words_tier_name;
        this.phones_tier_name = phones_tier_name;
        this.breaks_tier_name = breaks_tier_name;
        this.charsetName = charsetName;
    }

    /**
     * Generates a list of words from the associated TextGrid file.
     * <p/>
     * A list of words is generated, available ToBI information is aligned to them, and checked for consistency with the
     * standard.
     * <p/>
     * This is the main entry point for this class.
     * <p/>
     * Typical Usage:
     * <p/>
     * TextGridReader reader = new TextGridReader(filename) List<Words> data_points = reader.readWords();
     *
     * @return A list of words with from the TextGrid
     * @throws IOException                                    if there is a reader problem
     * @throws edu.cuny.qc.speech.AuToBI.core.AuToBIException if there is an alignment problem
     */
    public List<Word> readWords() throws IOException, AuToBIException {
        AuToBIFileReader file_reader;
        if (charsetName != null) {
            file_reader = new AuToBIFileReader(filename, charsetName);
        } else {
            file_reader = new AuToBIFileReader(filename);
        }

        Tier tier;
        readTextGridTier(file_reader);  // Remove TextGrid header
        do {
            tier = readTextGridTier(file_reader);

            if (tier.name != null && words_tier_name != null) {
                if (tier.name.equals(words_tier_name)) {
                    words_tier = tier;
                }
            } else if (tier.name != null && (tier.name.equals("words") || tier.name.equals("orthographic"))) {
                words_tier = tier;
            }

            if (tier.name != null && phones_tier_name != null) {
                if (tier.name.equals(phones_tier_name)) {
                    phones_tier = tier;
                }
            } else if (tier.name != null && tier.name.equals("phones")) {
                phones_tier = tier;
            }

            if (tier.name != null && breaks_tier_name != null) {
                if (tier.name.equals(breaks_tier_name)) {
                    breaks_tier = tier;
                }
            } else if (tier.name != null && tier.name.equals("breaks")) {
                breaks_tier = tier;
            }

        } while (tier.name != null);

        if (words_tier == null) {
            String tier_name = words_tier_name == null ? "'words' or 'orthographic'" : words_tier_name;
            throw new TextGridSyntaxErrorException("No words tier found with name, " + tier_name);
        }

        List<Word> words = generateWordList(words_tier.getRegions());

        if (phones_tier != null) {
            AlignmentUtils.copyToBITonesByTime(words, phones_tier.getRegions());
            if (breaks_tier == null || breaks_tier.getRegions().size() == 0) {
                AuToBIUtils.warn(
                        "Null or empty specified breaks tier found.  Default breaks will be generated from phrase ending tones in" +
                                " the tones tier.");
                ToBIUtils.generateBreaksFromTones(words);
            } else {
                try {
                    AlignmentUtils.copyToBIBreaks(words, breaks_tier.getRegions());
                } catch (AuToBIException e) {

                    for (int i = 0; i < words.size(); ++i) {
                        if (words.get(i).getEnd() != breaks_tier.getRegions().get(i).getStart()) {
                            AuToBIUtils.error("misaligned break at: " + breaks_tier.getRegions().get(i).getStart());
                        }
                    }
                    throw e;
                }
                ToBIUtils.checkToBIAnnotations(words);
            }
        } else if (breaks_tier != null) {
            AlignmentUtils.copyToBIBreaks(words, breaks_tier.getRegions());
            AuToBIUtils
                    .warn("No specified tones tier found.  Default phrase ending tones will be generated from breaks tier.");
            ToBIUtils.generateDefaultTonesFromBreaks(words);
        }

        //List<Word> words = generateWordList(words_tier.getRegions());

        copyToBIBreaksByTime(words, breaks_tier.getRegions());
        return words;
    }




    /**
     * Copies a list of breaks to associated words. Adapted from function of same name in AlignmentUtils.java.
     * <p/>
     * Requires that the breaks and words sorted by time. If a word does not have a break within its boundaries, it is
     * assumed to be a break index of '1'.
     * <p/>
     * Note: This should only be used where there is a strong trust that the annotation is correctly aligned with
     * segmental annotations.
     *
     * @param words  The list of words
     * @param breaks The list of breaks
     */
    public static void copyToBIBreaksByTime(List<Word> words, List<Region> breaks) {
        int break_idx = 0;
        int word_idx = 0;
        String previous_break = "na";
        while (break_idx < breaks.size() && word_idx < words.size()) {

            Region b = breaks.get(break_idx);
            Word word = words.get(word_idx);
            if (b.getStart() <= word.getStart()) { // consider a break as a point, only look at start time
                break_idx++;
            } else if (b.getStart() > word.getEnd() ) {
                if (word.getBreakAfter() == null) {
                    word.setBreakBefore(previous_break);
                    word.setBreakAfter("4");
                    previous_break = "4";
                }
                word_idx++;
            } else {
                // Assign break to word
                word.setBreakBefore(previous_break);
                String current_break = "4";
                word.setBreakAfter(current_break);
                // word.setPhraseAccent("X-?");
                // word.setBoundaryTone("X%?");
                previous_break = current_break;

                break_idx++;
                word_idx++;
            }
        }

        while (word_idx < words.size()) {
            String current_break = "na";
            words.get(word_idx).setBreakBefore(previous_break);
            words.get(word_idx).setBreakAfter(current_break);
            previous_break = current_break;
            word_idx++;
        }
    }
}
