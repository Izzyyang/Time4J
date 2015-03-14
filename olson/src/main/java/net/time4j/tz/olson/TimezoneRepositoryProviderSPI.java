/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (TimezoneRepositoryProviderSPI.java) is part of project Time4J.
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

package net.time4j.tz.olson;

import net.time4j.tz.NameStyle;
import net.time4j.tz.Timezone;
import net.time4j.tz.TransitionHistory;
import net.time4j.tz.ZoneProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * <p>Reads timezone repository-files compiled by the class
 * {@code net.time4j.tool.TimezoneRepositoryCompiler}. </p>
 *
 * @author  Meno Hochschild
 * @since   2.3
 * @exclude
 */
public class TimezoneRepositoryProviderSPI
    implements ZoneProvider {

    //~ Statische Felder/Initialisierungen --------------------------------

    private static final ZoneProvider NAME_PROVIDER = new ZoneNameProviderSPI();

    //~ Instanzvariablen --------------------------------------------------

    private final String version;
    private final String location;
    private final Map<String, TransitionHistory> data;
    private final Map<String, String> aliases;

    //~ Konstruktoren -----------------------------------------------------

    public TimezoneRepositoryProviderSPI() {
        super();

        URL url = null;
        InputStream is = null;
        ObjectInputStream ois = null;

        String tmpVersion = "";
        String tmpLocation = "";

        Map<String, TransitionHistory> tmpData =
            new HashMap<String, TransitionHistory>();
        Map<String, String> tmpAliases =
            new HashMap<String, String>();

        String repositoryPath =
            System.getProperty("net.time4j.tz.repository.path");
        String repositoryVersion =
            System.getProperty("net.time4j.tz.repository.version");
        String file;

        if (repositoryVersion == null) {
            file = "tzdata.repository";
        } else {
            file = "tzdata" + repositoryVersion + ".repository";
        }

        try {
            if (repositoryPath != null) {
                File path = new File(repositoryPath, file);

                if (path.isAbsolute()) {
                    if (path.exists()) {
                        url = path.toURI().toURL();
                    } else {
                        throw new FileNotFoundException(
                            "Path to tz-repository not found: " + path);
                    }
                } else {
                    String internalResource = path.toString();
                    url = classLoader().getResource(internalResource);
                }
            } else {
                String internalResource = "tzrepo/" + file;
                url = classLoader().getResource(internalResource);
            }

            if (url != null) {
                is = url.openStream();
                ois = new ObjectInputStream(is);
                tmpLocation = url.toString();
                checkMagicLabel(ois, tmpLocation);
                String v = ois.readUTF();
                int sizeOfZones = ois.readInt();

                List<String> zones = new ArrayList<String>();

                for (int i = 0; i < sizeOfZones; i++) {
                    String zoneID = ois.readUTF();
                    TransitionHistory th = (TransitionHistory) ois.readObject();
                    zones.add(zoneID);
                    tmpData.put(zoneID, th);
                }

                int sizeOfLinks = ois.readShort();

                for (int i = 0; i < sizeOfLinks; i++) {
                    String alias = ois.readUTF();
                    String id = zones.get(ois.readShort());
                    tmpAliases.put(alias, id);
                }

                int sizeOfLeaps = ois.readShort();

                for (int i = 0; i < sizeOfLeaps; i++) {
                    int year = ois.readShort();
                    int month = ois.readByte();
                    int dom = ois.readByte();
                    int shift = ois.readByte();
                    // TODO: register leapseconds
                }

                tmpVersion = v; // here all is okay, so let us set the version
            }

        } catch (ClassNotFoundException cnfe) {
            System.out.println(
                "Note: TZ-repository corrupt. => " + cnfe.getMessage());
        } catch (IOException ioe) {
            System.out.println(
                "Note: TZ-repository not available. => " + ioe.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    // ignored
                }
            }
        }

        this.version = tmpVersion;
        this.location = tmpLocation;
        this.data = Collections.unmodifiableMap(tmpData);
        this.aliases = Collections.unmodifiableMap(tmpAliases);

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public Set<String> getAvailableIDs() {

        return this.data.keySet();

    }

    @Override
    public Set<String> getPreferredIDs(
        Locale locale,
        boolean smart
    ) {

        return NAME_PROVIDER.getPreferredIDs(locale, smart);

    }

    @Override
    public String getDisplayName(
        String tzid,
        NameStyle style,
        Locale locale
    ) {

        Timezone tz = Timezone.of("java.util.TimeZone~" + tzid);
        return tz.getDisplayName(style, locale);

    }

    @Override
    public Map<String, String> getAliases() {

        return this.aliases;

    }

    @Override
    public TransitionHistory load(String zoneID) {

        return this.data.get(zoneID);

    }

    @Override
    public String getFallback() {

        return "";

    }

    @Override
    public String getName() {

        return "TZDB";

    }

    @Override
    public String getLocation() {

        return this.location;

    }

    @Override
    public String getVersion() {

        return this.version;

    }

    private static void checkMagicLabel(
        ObjectInputStream ois,
        String location
    ) throws IOException {

        int b1 = ois.readByte();
        int b2 = ois.readByte();
        int b3 = ois.readByte();
        int b4 = ois.readByte();
        int b5 = ois.readByte();
        int b6 = ois.readByte();

        if (
            (b1 != 't')
            || (b2 != 'z')
            || (b3 != 'r')
            || (b4 != 'e')
            || (b5 != 'p')
            || (b6 != 'o')
        )  {
            throw new IOException("Invalid tz-repository: " + location);
        }

    }

    private static ClassLoader classLoader() {

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        if (cl == null) {
            cl = ZoneProvider.class.getClassLoader();
        }

        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        return cl;

    }

}