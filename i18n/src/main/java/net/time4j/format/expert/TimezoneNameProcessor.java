/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (TimezoneNameProcessor.java) is part of project Time4J.
 *
 * Time4J is free software: You can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * Time4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Time4J. If not, see <http://www.gnu.org/licenses/>.
 * -----------------------------------------------------------------------
 */

package net.time4j.format.expert;

import net.time4j.base.UnixTime;
import net.time4j.engine.AttributeQuery;
import net.time4j.engine.ChronoDisplay;
import net.time4j.engine.ChronoElement;
import net.time4j.format.Attributes;
import net.time4j.format.Leniency;
import net.time4j.tz.NameStyle;
import net.time4j.tz.TZID;
import net.time4j.tz.Timezone;
import net.time4j.tz.ZonalOffset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * <p>Verarbeitet einen Zeitzonen-Namen. </p>
 *
 * @author  Meno Hochschild
 * @since   3.0
 * @doctags.concurrency <immutable>
 */
final class TimezoneNameProcessor
    implements FormatProcessor<TZID> {

    //~ Statische Felder/Initialisierungen --------------------------------

    private static final ConcurrentMap<Locale, TZNames> CACHE_ABBREVIATIONS =
        new ConcurrentHashMap<Locale, TZNames>();
    private static final ConcurrentMap<Locale, TZNames> CACHE_ZONENAMES =
        new ConcurrentHashMap<Locale, TZNames>();
    private static final int MAX = 25; // maximum size of cache
    private static final String DEFAULT_PROVIDER = "DEFAULT";

    //~ Instanzvariablen --------------------------------------------------

    private final boolean abbreviated;
    private final FormatProcessor<TZID> fallback;
    private final Set<TZID> preferredZones;

    //~ Konstruktoren -----------------------------------------------------

    /**
     * <p>Erzeugt eine neue Instanz. </p>
     *
     * @param   abbreviated     abbreviations to be used?
     */
    TimezoneNameProcessor(boolean abbreviated) {
        super();

        this.abbreviated = abbreviated;
        this.fallback = new LocalizedGMTProcessor(abbreviated);
        this.preferredZones = null;

    }

    /**
     * <p>Erzeugt eine neue Instanz. </p>
     *
     * @param   abbreviated     abbreviations to be used?
     * @param   preferredZones  preferred timezone ids for resolving duplicates
     */
    TimezoneNameProcessor(
        boolean abbreviated,
        Set<TZID> preferredZones
    ) {
        super();

        this.abbreviated = abbreviated;
        this.fallback = new LocalizedGMTProcessor(abbreviated);
        this.preferredZones = Collections.unmodifiableSet(new LinkedHashSet<TZID>(preferredZones));

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public void print(
        ChronoDisplay formattable,
        Appendable buffer,
        AttributeQuery attributes,
        Set<ElementPosition> positions,
        FormatStep step
    ) throws IOException {

        if (!formattable.hasTimezone()) {
            throw new IllegalArgumentException(
                "Cannot extract timezone id from: " + formattable);
        }

        TZID tzid = formattable.getTimezone();

        if (tzid instanceof ZonalOffset) {
            this.fallback.print(
                formattable, buffer, attributes, positions, step);
            return;
        }

        String name;

        if (formattable instanceof UnixTime) {
            Timezone zone = Timezone.of(tzid);
            UnixTime ut = UnixTime.class.cast(formattable);

            name =
                zone.getDisplayName(
                    this.getStyle(zone.isDaylightSaving(ut)),
                    step.getAttribute(Attributes.LANGUAGE, attributes, Locale.ROOT));
        } else {
            throw new IllegalArgumentException(
                "Cannot extract timezone name from: " + formattable);
        }

        int start = -1;
        int printed;

        if (buffer instanceof CharSequence) {
            start = ((CharSequence) buffer).length();
        }

        buffer.append(name);
        printed = name.length();

        if (
            (start != -1)
            && (printed > 0)
            && (positions != null)
        ) {
            positions.add(
                new ElementPosition(
                    TimezoneElement.TIMEZONE_ID,
                    start,
                    start + printed));
        }

    }

    @Override
    public void parse(
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes,
        Map<ChronoElement<?>, Object> parsedResult,
        FormatStep step
    ) {

        int len = text.length();
        int start = status.getPosition();
        int pos = start;

        if (pos >= len) {
            status.setError(start, "Missing timezone name.");
            return;
        }

        Locale locale =
            step.getAttribute(Attributes.LANGUAGE, attributes, Locale.ROOT);
        Leniency leniency =
            step.getAttribute(Attributes.LENIENCY, attributes, Leniency.SMART);

        // evaluation of relevant part of input which might contain the timezone name
        StringBuilder name = new StringBuilder();

        while (pos < len) {
            char c = text.charAt(pos);

            if (
                Character.isLetter(c) // tz names must start with a letter
                || (!this.abbreviated && (pos > start) && !Character.isDigit(c))
            ) {
                // long tz names can contain almost every char - with the exception of digits
                name.append(c);
                pos++;
            } else {
                break;
            }
        }

        String key = name.toString().trim();
        pos = start + key.length();

        // fallback-case (fixed offset)
        if (
            key.startsWith("GMT")
            || key.startsWith("UT")
        ) {
            this.fallback.parse(text, status, attributes, parsedResult, step);
            return;
        }

        // Zeitzonennamen im Cache suchen und ggf. Cache füllen
        ConcurrentMap<Locale, TZNames> cache = (
            this.abbreviated
            ? CACHE_ABBREVIATIONS
            : CACHE_ZONENAMES);

        TZNames tzNames = cache.get(locale);

        if (tzNames == null) {
            Map<String, List<TZID>> stdNames =
                this.getTimezoneNameMap(locale, false);
            Map<String, List<TZID>> dstNames =
                this.getTimezoneNameMap(locale, true);
            tzNames = new TZNames(stdNames, dstNames);

            if (cache.size() < MAX) {
                TZNames tmp = cache.putIfAbsent(locale, tzNames);

                if (tmp != null) {
                    tzNames = tmp;
                }
            }
        }

        // Zeitzonen-IDs bestimmen
        int[] lenbuf = new int[2];
        lenbuf[0] = pos;
        lenbuf[1] = pos;
        List<TZID> stdZones = readZones(tzNames, key, false, lenbuf);
        List<TZID> dstZones = readZones(tzNames, key, true, lenbuf);
        int sum = stdZones.size() + dstZones.size();

        if (sum == 0) {
            status.setError(
                start,
                "Unknown timezone name: " + key);
            return;
        }
        
        if (
            (sum > 1)
            && !leniency.isLax()
        ) { // tz name not unique
            if (stdZones.size() > 0) {
                stdZones = this.resolve(stdZones, locale, leniency);
            }
            if (dstZones.size() > 0) {
                dstZones = this.resolve(dstZones, locale, leniency);
            }
        }

        sum = stdZones.size() + dstZones.size();
        
        if (sum == 0) {
            status.setError(
                start,
                "Time zone id not found among preferred timezones in locale " 
                + locale);
            return;
        }

        List<TZID> zones;
        int index;

        if (stdZones.size() > 0) {
            zones = stdZones;
            index = 0;
            if (
                (sum == 2)
                && (dstZones.size() > 0)
                && (stdZones.get(0).canonical().equals(dstZones.get(0).canonical()))
            ) {
                dstZones.remove(0);
                sum--;
            } else if (!dstZones.isEmpty()) {
                zones = new ArrayList<TZID>(zones);
                zones.addAll(dstZones); // for better error message if not unique
            }
        } else {
            zones = dstZones;
            index = 1;
        }

        // remove alternative provider zones if default provider zone exists
        if (sum > 1) {
            List<TZID> filtered = null;
            for (TZID id : zones) {
                if (id.canonical().indexOf('~') == -1) {
                    if (filtered == null) {
                        filtered = new ArrayList<TZID>();
                    }
                    filtered.add(id);
                }
            }
            if (filtered != null) {
                zones = filtered;
                sum = zones.size();
            }
        }

        // final step: determining the result
        if (
            (sum == 1)
            || leniency.isLax()
        ) {
            parsedResult.put(TimezoneElement.TIMEZONE_ID, zones.get(0));
            status.setPosition(lenbuf[index]);
            if (tzNames.isDaylightSensitive()) {
                status.setDaylightSaving(index == 1);
            }
        } else {
            status.setError(
                start,
                "Time zone name is not unique: \"" + key + "\" in "
                + toString(zones));
        }

    }

    @Override
    public ChronoElement<TZID> getElement() {

        return TimezoneElement.TIMEZONE_ID;

    }

    @Override
    public FormatProcessor<TZID> withElement(ChronoElement<TZID> element) {

        return this;

    }

    @Override
    public boolean isNumerical() {

        return false;

    }

    private Map<String, List<TZID>> getTimezoneNameMap(
        Locale locale,
        boolean daylightSaving
    ) {

        List<TZID> zones;
        Map<String, List<TZID>> map = new HashMap<String, List<TZID>>();

        for (TZID tzid : Timezone.getAvailableIDs()) {
            Timezone zone = Timezone.of(tzid);

            String tzName =
                zone.getDisplayName(
                    this.getStyle(daylightSaving),
                    locale);

            if (tzName.equals(tzid.canonical())) {
                continue; // registrierte NameProvider haben nichts gefunden!
            }

            zones = map.get(tzName);

            if (zones == null) {
                zones = new ArrayList<TZID>();
                map.put(tzName, zones);
            }

            zones.add(tzid);
        }

        return Collections.unmodifiableMap(map);

    }

    private static List<TZID> readZones(
        TZNames tzNames,
        String key,
        boolean daylightSaving,
        int[] lenbuf
    ) {
        
        List<TZID> zones = tzNames.search(key, daylightSaving);

        if (zones.isEmpty()) {
            int last = key.length() - 1;
            if (!Character.isLetter(key.charAt(last))) { // maybe interpunctuation char?
                zones = tzNames.search(key.substring(0, last), daylightSaving);
                if (!zones.isEmpty()) {
                    int index = (daylightSaving ? 1 : 0);
                    lenbuf[index]--;
                }
            }
        }
        
        return zones;
        
    }
    
    private List<TZID> resolve(
        List<TZID> zones,
        Locale locale,
        Leniency leniency
    ) {

        Map<String, List<TZID>> matched = new HashMap<String, List<TZID>>();
        matched.put(DEFAULT_PROVIDER, new ArrayList<TZID>());

        for (TZID tz : zones) {
            String id = tz.canonical();
            Set<TZID> prefs = this.preferredZones;
            String provider = DEFAULT_PROVIDER;
            int index = id.indexOf('~');

            if (index >= 0) {
                provider = id.substring(0, index);
            }

            if (prefs == null) {
                prefs =
                    Timezone.getPreferredIDs(
                        locale,
                        leniency.isSmart(),
                        provider);
            }

            for (TZID p : prefs) {
                if (p.canonical().equals(id)) {
                    List<TZID> candidates = matched.get(provider);
                    if (candidates == null) {
                        candidates = new ArrayList<TZID>();
                        matched.put(provider, candidates);
                    }
                    candidates.add(p);
                    break;
                }
            }
        }

        List<TZID> candidates = matched.get(DEFAULT_PROVIDER);

        if (candidates.isEmpty()) {
            matched.remove(DEFAULT_PROVIDER);
            boolean found = false;
            for (String provider : matched.keySet()) {
                candidates = matched.get(provider);
                if (!candidates.isEmpty()) {
                    found = true;
                    zones = candidates;
                    break;
                }
            }
            if (!found) {
                zones = Collections.emptyList();
            }
        } else {
            zones = candidates;
        }

        return zones;

    }
    
    private NameStyle getStyle(boolean daylightSaving) {

        if (daylightSaving) {
            return (
                this.abbreviated
                ? NameStyle.SHORT_DAYLIGHT_TIME
                : NameStyle.LONG_DAYLIGHT_TIME);
        } else {
            return (
                this.abbreviated
                ? NameStyle.SHORT_STANDARD_TIME
                : NameStyle.LONG_STANDARD_TIME);
        }

    }

    private static String toString(List<TZID> ids) {

        StringBuilder sb = new StringBuilder(ids.size() * 16);
        sb.append('{');
        boolean first = true;

        for (TZID tzid : ids) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(tzid.canonical());
        }

        return sb.append('}').toString();

    }

    //~ Innere Klassen ----------------------------------------------------

    private static class TZNames {

        //~ Instanzvariablen ----------------------------------------------

        private final boolean dstSensitive;
        private final Map<String, List<TZID>> stdNames;
        private final Map<String, List<TZID>> dstNames;

        //~ Konstruktoren -------------------------------------------------

        TZNames(
            Map<String, List<TZID>> stdNames,
            Map<String, List<TZID>> dstNames
        ) {
            super();

            this.stdNames = stdNames;
            this.dstNames = dstNames;

            this.dstSensitive = !stdNames.keySet().equals(dstNames.keySet());

        }

        //~ Methoden ------------------------------------------------------

        boolean isDaylightSensitive() {

            return this.dstSensitive;

        }

        // quick search via hash-access
        List<TZID> search(
            String key,
            boolean daylightSaving
        ) {

            Map<String, List<TZID>> names = (
                daylightSaving
                ? this.dstNames
                : this.stdNames);

            if (names.containsKey(key)) {
                return names.get(key);
            }

            return Collections.emptyList();

        }

    }

}
