/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (WeekdataProviderSPI.java) is part of project Time4J.
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

package net.time4j.i18n;

import net.time4j.Weekday;
import net.time4j.Weekmodel;
import net.time4j.format.WeekdataProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * <p>Standard-SPI-Implementierung eines {@code WeekdataProvider}. </p>
 *
 * @author  Meno Hochschild
 * @since   2.2
 */
public class WeekdataProviderSPI
    implements WeekdataProvider {

    //~ Statische Felder/Initialisierungen --------------------------------

    static final Map<String, Weekday> START_OF_WEEKEND;
    static final Map<String, Weekday> END_OF_WEEKEND;

    // Daten aus CLDR 23
    static {
        Map<String, Weekday> tmp = new HashMap<>(28);
        tmp.put("AF", Weekday.THURSDAY);
        tmp.put("DZ", Weekday.THURSDAY);
        tmp.put("IR", Weekday.THURSDAY);
        tmp.put("OM", Weekday.THURSDAY);
        tmp.put("SA", Weekday.THURSDAY);
        tmp.put("YE", Weekday.THURSDAY);
        tmp.put("AE", Weekday.FRIDAY);
        tmp.put("BH", Weekday.FRIDAY);
        tmp.put("EG", Weekday.FRIDAY);
        tmp.put("IL", Weekday.FRIDAY);
        tmp.put("IQ", Weekday.FRIDAY);
        tmp.put("JO", Weekday.FRIDAY);
        tmp.put("KW", Weekday.FRIDAY);
        tmp.put("LY", Weekday.FRIDAY);
        tmp.put("MA", Weekday.FRIDAY);
        tmp.put("QA", Weekday.FRIDAY);
        tmp.put("SD", Weekday.FRIDAY);
        tmp.put("SY", Weekday.FRIDAY);
        tmp.put("TN", Weekday.FRIDAY);
        tmp.put("IN", Weekday.SUNDAY);
        START_OF_WEEKEND = Collections.unmodifiableMap(tmp);

        tmp = new HashMap<>(25);
        tmp.put("AF", Weekday.FRIDAY);
        tmp.put("DZ", Weekday.FRIDAY);
        tmp.put("IR", Weekday.FRIDAY);
        tmp.put("OM", Weekday.FRIDAY);
        tmp.put("SA", Weekday.FRIDAY);
        tmp.put("YE", Weekday.FRIDAY);
        tmp.put("AE", Weekday.SATURDAY);
        tmp.put("BH", Weekday.SATURDAY);
        tmp.put("EG", Weekday.SATURDAY);
        tmp.put("IL", Weekday.SATURDAY);
        tmp.put("IQ", Weekday.SATURDAY);
        tmp.put("JO", Weekday.SATURDAY);
        tmp.put("KW", Weekday.SATURDAY);
        tmp.put("LY", Weekday.SATURDAY);
        tmp.put("MA", Weekday.SATURDAY);
        tmp.put("QA", Weekday.SATURDAY);
        tmp.put("SD", Weekday.SATURDAY);
        tmp.put("SY", Weekday.SATURDAY);
        tmp.put("TN", Weekday.SATURDAY);
        END_OF_WEEKEND = Collections.unmodifiableMap(tmp);
    }

    //~ Instanzvariablen --------------------------------------------------

    private final String source;
    private final Map<String, Weekday> startOfWeekend;
    private final Map<String, Weekday> endOfWeekend;

    //~ Konstruktoren -----------------------------------------------------

    public WeekdataProviderSPI() {
        super();

        InputStream is = null;
        String name = "data/weekend.data";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        if (cl != null) {
            is = cl.getResourceAsStream(name);
        }

        if (is == null) {
            cl = Weekmodel.class.getClassLoader();
            is = cl.getResourceAsStream(name);
        }

        if (is != null) {

            this.source = "@" + cl.getResource(name).toString();
            Map<String, Weekday> tmpStart = new HashMap<>(START_OF_WEEKEND.size());
            Map<String, Weekday> tmpEnd = new HashMap<>(END_OF_WEEKEND.size());

            try {

                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(is, "US-ASCII"));

                String line;

                while ((line = br.readLine()) != null) {

                    if (line.startsWith("#")) {
                        continue; // Kommentarzeile überspringen
                    }

                    int equal = line.indexOf('=');
                    String prefix = line.substring(0, equal).trim();
                    String[] list = line.substring(equal + 1).split(" ");
                    String wd;
                    Weekday weekday;
                    Map<String, Weekday> map;

                    if (prefix.startsWith("start-")) {
                        wd = prefix.substring(6);
                        weekday = Weekday.SATURDAY;
                        map = tmpStart;
                    } else if (prefix.startsWith("end-")) {
                        wd = prefix.substring(4);
                        weekday = Weekday.SUNDAY;
                        map = tmpEnd;
                    } else {
                        throw new IllegalStateException(
                            "Unexpected format: " + this.source);
                    }

                    switch (wd) {
                        case "sun":
                            weekday = Weekday.SUNDAY;
                            break;
                        case "sat":
                            weekday = Weekday.SATURDAY;
                            break;
                        case "fri":
                            weekday = Weekday.FRIDAY;
                            break;
                        case "thu":
                            weekday = Weekday.THURSDAY;
                            break;
                        case "wed":
                            weekday = Weekday.WEDNESDAY;
                            break;
                        case "tue":
                            weekday = Weekday.TUESDAY;
                            break;
                        case "mon":
                            weekday = Weekday.MONDAY;
                            break;
                    }

                    for (String country : list) {
                        String key = country.trim().toUpperCase(Locale.US);

                        if (!key.isEmpty()) {
                            map.put(key, weekday);
                        }
                    }

                }

                this.startOfWeekend = Collections.unmodifiableMap(tmpStart);
                this.endOfWeekend = Collections.unmodifiableMap(tmpEnd);

            } catch (UnsupportedEncodingException uee) {
                throw new AssertionError(uee);
            } catch (Exception ex) {
                throw new IllegalStateException(
                    "Unexpected format: " + this.source, ex);
            } finally {
                try {
                    is.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace(System.err);
                }
            }

        } else {
            this.source = "@STATIC";
            this.startOfWeekend = START_OF_WEEKEND;
            this.endOfWeekend = END_OF_WEEKEND;

            System.out.println("Warning: File \"" + name + "\" not found.");
        }

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public int getFirstDayOfWeek(Locale country) {

        // TODO: CLDR-Daten anzapfen?
        GregorianCalendar gc = new GregorianCalendar(country);
        int fd = gc.getFirstDayOfWeek();
        return ((fd == 1) ? 7 : (fd - 1));

    }

    @Override
    public int getMinimalDaysInFirstWeek(Locale country) {

        // TODO: CLDR-Daten anzapfen?
        GregorianCalendar gc = new GregorianCalendar(country);
        return gc.getMinimalDaysInFirstWeek();

    }

    @Override
    public int getStartOfWeekend(Locale country) {

        String key = country.getCountry();
        Weekday start = Weekday.SATURDAY;

        if (this.startOfWeekend.containsKey(key)) {
            start = this.startOfWeekend.get(key);
        }

        return start.getValue();

    }

    @Override
    public int getEndOfWeekend(Locale country) {

        String key = country.getCountry();
        Weekday end = Weekday.SUNDAY;

        if (this.endOfWeekend.containsKey(key)) {
            end = this.endOfWeekend.get(key);
        }

        return end.getValue();

    }

    @Override
    public String toString() {

        return this.getClass().getName() + this.source;

    }

}
