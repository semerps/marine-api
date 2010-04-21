/* 
 * SentenceParser.java
 * Copyright (C) 2010 Kimmo Tuukkanen
 * 
 * This file is part of Java Marine API.
 * <http://sourceforge.net/projects/marineapi/>
 * 
 * Java Marine API is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * Java Marine API is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Marine API. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.marineapi.nmea.parser;

import java.util.ArrayList;
import java.util.List;

import net.sf.marineapi.nmea.sentence.NMEA;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.SentenceId;
import net.sf.marineapi.nmea.sentence.TalkerId;

/**
 * <p>
 * Base sentence parser for all NMEA sentence types. Provides general services
 * such as sentence validation, field parsing and formatting.
 * <p>
 * NMEA data is transmitted in the form of "sentences". Each sentence starts
 * with a "$", a two letter "talker ID", a three letter "sentence ID", followed
 * by a number of data fields separated by commas, and terminated by an optional
 * checksum, and a carriage return/line feed. A sentence may contain up to 82
 * characters including the "$" and CR/LF. If data for a field is not available,
 * the field is simply omitted, but the commas that would delimit it are still
 * sent, with no space between them.
 * <p>
 * <code>
 * $&lt;address field&gt;,&lt;data field #1&gt;,&lt;data field #2&gt;,...,&lt;data field #n&gt;*&lt;checksum&gt;
 * </code>
 * <p>
 * For more details on sentence format, see <a
 * href="http://vancouver-webpages.com/peter/nmeafaq.txt">NMEA FAQ</a> by Peter
 * Bennett or <a href="http://gpsd.berlios.de/NMEA.txt">NMEA Revealed</a> by
 * Eric S. Raymond.
 * 
 * @author Kimmo Tuukkanen
 * @version $Revision$
 */
public class SentenceParser implements Sentence {

    // The first two characters after '$'.
    private TalkerId talkerId;

    // The next three characters after talker id.
    private final SentenceId sentenceId;

    // data fields, address and checksum fields omitted
    private List<String> fields;

    /**
     * Creates a new instance of SentenceParser. Sentence type is resolved
     * automatically and checked against the supported types.
     * 
     * @param nmea A valid NMEA 0183 sentence
     * @throws IllegalArgumentException If the specified sentence is invalid or
     *             if sentence type is not supported.
     */
    public SentenceParser(String nmea) {
        // check for valid sentence string
        if (!NMEA.isValid(nmea)) {
            String msg = String.format("Invalid data [%s]", nmea);
            throw new IllegalArgumentException(msg);
        }

        talkerId = parseTalkerId(nmea);
        sentenceId = parseSentenceId(nmea);

        // remove address field
        int begin = nmea.indexOf(Sentence.FIELD_DELIMITER);
        String temp = nmea.substring(begin + 1);

        // remove checksum
        if (temp.contains(String.valueOf(CHECKSUM_DELIMITER))) {
            int end = temp.indexOf(CHECKSUM_DELIMITER);
            temp = temp.substring(0, end);
        }

        // copy data to fields to List
        String[] temp2 = temp.split(String.valueOf(FIELD_DELIMITER), -1);
        fields = new ArrayList<String>(temp2.length);
        for (String s : temp2) {
            fields.add(s);
        }
    }

    /**
     * Creates a new instance of SentenceParser. Sentence may be constructed
     * only if parameter <code>nmea</code> contains a valid NMEA 0183 sentence
     * of the specified <code>type</code>.
     * <p>
     * For example, GGAParser class should specify the type
     * <code>SentenceId.GGA</code> as <code>type</code> to succeed.
     * 
     * @param nmea NMEA sentence <code>String</code>
     * @param type Type of the sentence in <code>nmea</code> parameter
     * @throws IllegalArgumentException If the given String is not a valid NMEA
     *             0831 sentence or does not match the specified type.
     */
    protected SentenceParser(String nmea, SentenceId type) {
        this(nmea);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Sentence type must be specified");
        }
        SentenceId id = getSentenceId();
        if (id != type) {
            String msg = String.format("Sentence type mismatch [%s]", id);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Creates a new empty sentence with specified talker and sentence IDs.
     * 
     * @param talker Talker type Id
     * @param type Sentence type Id
     * @param size Number of sentence data fields, including address field
     */
    protected SentenceParser(TalkerId talker, SentenceId type, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Minimum number of fields is 1");
        }

        if (talker == null || type == null) {
            throw new IllegalArgumentException(
                    "Sentence and Talker IDs must be specified");
        }

        this.talkerId = talker;
        this.sentenceId = type;

        fields = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            fields.add("");
        }
    }

    /*
     * (non-Javadoc)
     * @see net.sf.marineapi.nmea.sentence.Sentence#getSentenceId()
     */
    public SentenceId getSentenceId() {
        return sentenceId;
    }

    /*
     * (non-Javadoc)
     * @see net.sf.marineapi.nmea.sentence.Sentence#getTalkerId()
     */
    public TalkerId getTalkerId() {
        return talkerId;
    }

    /*
     * (non-Javadoc)
     * @see
     * net.sf.marineapi.nmea.sentence.Sentence#setTalkerId(net.sf.marineapi.
     * nmea.util.TalkerId)
     */
    public void setTalkerId(TalkerId id) {
        this.talkerId = id;
    }

    /**
     * Get the String representation of the sentence without the line terminator
     * (&lt;CR&gt;&lt;LF&gt;). Checksum is calculated and appended at the end of
     * the sentence.
     * 
     * @return NMEA 0183 sentence String
     * @throws IllegalStateException If formatting results in invalid sentence.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(MAX_LENGTH);
        sb.append(BEGIN_CHAR);
        sb.append(talkerId.toString());
        sb.append(sentenceId.toString());

        // for (int i = 1; i < fields.size(); i++) {
        // sb.append(FIELD_DELIMITER);
        // sb.append(fields.get(i));
        // }
        for (String field : fields) {
            sb.append(FIELD_DELIMITER);
            sb.append(field);
        }

        String sentence = NMEA.appendChecksum(sb.toString());
        if (!NMEA.isValid(sentence)) {
            String msg = String.format("Invalid result [%s]", sentence);
            throw new IllegalStateException(msg);
        }

        return sentence;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof SentenceParser) {
            Sentence s = (Sentence) o;
            return s.toString().equals(toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Parse a single character from the specified sentence field.
     * 
     * @param index Data field index in sentence
     * @return Character contained in the field
     * @throws ParseException If field contains more than one character
     */
    protected final char getCharValue(int index) {
        String val = getStringValue(index);
        if (val.length() > 1) {
            String msg = String.format("Expected char, found String [%s]", val);
            throw new ParseException(msg);
        }
        return val.charAt(0);
    }

    /**
     * Parse double value from the specified sentence field.
     * 
     * @param index Data field index in sentence
     * @return Field as parsed by <code>Double.parseDouble()</code>
     */
    protected final double getDoubleValue(int index) {
        double value;
        try {
            value = Double.parseDouble(getStringValue(index));
        } catch (NumberFormatException ex) {
            throw new ParseException("Field does not contain double value", ex);
        }
        return value;
    }

    /**
     * Returns the number of data fields in sentence, including the address
     * field and excluding checksum.
     * 
     * @return The number of fields
     */
    protected final int getFieldCount() {
        return fields.size();
    }

    /**
     * Parse integer value from the specified sentence field.
     * 
     * @param index Field index in sentence
     * @return Field parsed by <code>Integer.parseInt()</code>
     */
    protected final int getIntValue(int index) {
        int value;
        try {
            value = Integer.parseInt(getStringValue(index));
        } catch (NumberFormatException ex) {
            throw new ParseException("Field does not contain integer value", ex);
        }
        return value;
    }

    /**
     * Get contents of a data field as a String. Field indexing is zero-based.
     * The address field (e.g. <code>$GPGGA</code>) and checksum at the end are
     * not considered as a data fields and cannot therefore be fetched with this
     * method.
     * <p>
     * Field indexing, let i = 1: <br>
     * <code>$&lt;id&gt;,&lt;i&gt;,&lt;i+1&gt;,&lt;i+2&gt;,...,&lt;i+n&gt;*&lt;checksum&gt;</code>
     * 
     * @param index Field index
     * @return Field value as String
     * @throws DataNotAvailableException If the field is empty
     */
    protected final String getStringValue(int index) {
        String value = fields.get(index);
        if (value == null || "".equals(value)) {
            throw new DataNotAvailableException("Data not available");
        }
        return value;
    }

    /**
     * Tells is if the field specified by the given index contains a value.
     * 
     * @param index Field index
     * @return True if field contains value, otherwise false.
     */
    protected final boolean hasValue(int index) {
        boolean result = true;
        try {
            getStringValue(index);
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    /**
     * Set a character in specified field.
     * 
     * @param index Field index
     * @param value Value to set
     */
    protected final void setCharValue(int index, char value) {
        setStringValue(index, String.valueOf(value));
    }

    /**
     * Set a double value in specified field.
     * 
     * @param index Field index
     * @param value Value to set
     */
    protected final void setDoubleValue(int index, double value) {
        // TODO add support for rounding to defined decimal precision
        setStringValue(index, String.valueOf(value));
    }

    /**
     * Set a integer value in specified field.
     * 
     * @param index Field index
     * @param value Value to set
     */
    protected final void setIntValue(int index, int value) {
        setStringValue(index, String.valueOf(value));
    }

    /**
     * Set a String value in specified data field.
     * 
     * @param index Field index
     * @param value String to set, <code>null</code> converts to empty String.
     */
    protected final void setStringValue(int index, String value) {
        if (index < 0) {
            throw new IllegalArgumentException("Index must be > 1");
        }
        fields.set(index, value == null ? "" : value);
    }

    /**
     * Parses the sentence Id of specified NMEA sentence.
     * 
     * @param nmea NMEA 0183 sentence String
     * @return SentenceId Sentence ID of the specified sentence
     */
    static SentenceId parseSentenceId(String nmea) {
        String sid = nmea.substring(3, 6);
        try {
            return SentenceId.valueOf(sid);
        } catch (Exception e) {
            String msg = String.format("Unsupported sentence Id [%s]", sid);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Parses the talker Id of specified NMEA sentence.
     * 
     * @param nmea NMEA 0183 sentence String
     * @return TalkerId Talker ID of the specified sentence
     */
    static TalkerId parseTalkerId(String nmea) {
        String tid = nmea.substring(1, 3);
        try {
            return TalkerId.valueOf(tid);
        } catch (Exception e) {
            String msg = String.format("Unsupported talker Id [%s]", tid);
            throw new IllegalArgumentException(msg);
        }
    }

}