/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (CalendarAlgorithm.java) is part of project Time4J.
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

package net.time4j.history;

import net.time4j.base.GregorianMath;


/**
 * <p>Represents a calendar algorithm as computation machine. </p>
 *
 * @author  Meno Hochschild
 * @since   3.0
 */
enum CalendarAlgorithm {

    //~ Statische Felder/Initialisierungen --------------------------------

    GREGORIAN {
        @Override
        long toMJD(HistoricDate date) {
            return GregorianMath.toMJD(getProlepticYear(date), date.getMonth(), date.getDayOfMonth());
        }

        @Override
        HistoricDate fromMJD(long mjd) {
            long packed = GregorianMath.toPackedDate(mjd);
            int year = GregorianMath.readYear(packed);
            int month = GregorianMath.readMonth(packed);
            int dom = GregorianMath.readDayOfMonth(packed);
            return new HistoricDate(
                (year <= 0) ? HistoricEra.BC : HistoricEra.AD,
                (year <= 0) ? 1 - year : year,
                month,
                dom
            );
        }

        @Override
        boolean isValid(HistoricDate date) {
            return GregorianMath.isValid(getProlepticYear(date), date.getMonth(), date.getDayOfMonth());
        }

        @Override
        int getMaximumDayOfMonth(HistoricDate date) {
            return GregorianMath.getLengthOfMonth(getProlepticYear(date), date.getMonth());
        }
    },

    JULIAN {
        @Override
        long toMJD(HistoricDate date) {
            return JulianMath.toMJD(getProlepticYear(date), date.getMonth(), date.getDayOfMonth());
        }

        @Override
        HistoricDate fromMJD(long mjd) {
            long packed = JulianMath.toPackedDate(mjd);
            int year = JulianMath.readYear(packed);
            int month = JulianMath.readMonth(packed);
            int dom = JulianMath.readDayOfMonth(packed);
            return new HistoricDate(
                (year <= 0) ? HistoricEra.BC : HistoricEra.AD,
                (year <= 0) ? 1 - year : year,
                month,
                dom
            );
        }

        @Override
        boolean isValid(HistoricDate date) {
            int year = getProlepticYear(date);
            int month = date.getMonth();
            int dom = date.getDayOfMonth();
            if (
                (year < -999979466) // value limit of PlainDate.axis().getMinimum()
                || ((year == -999979466) && ((month < 11) || ((month == 11) && (dom < 21))))
            ) {
                return false;
            }
            return JulianMath.isValid(year, month, dom);
        }

        @Override
        int getMaximumDayOfMonth(HistoricDate date) {
            return JulianMath.getLengthOfMonth(getProlepticYear(date), date.getMonth());
        }
    },

    // das betrifft nur den Abschnitt mit der schwedischen Anomalie im Zeitraum 1700-03-01/1712-02-30
    SWEDISH {
        @Override
        long toMJD(HistoricDate date) {
            if (
                (date.getDayOfMonth() == 30)
                && (date.getMonth() == 2)
                && (date.getYearOfEra() == 1712)
                && (date.getEra() == HistoricEra.AD)
            ) {
                return -53576L;
            }
            return JulianMath.toMJD(date.getYearOfEra(), date.getMonth(), date.getDayOfMonth()) - 1;
        }

        @Override
        HistoricDate fromMJD(long mjd) {
            if (mjd == -53576L) {
                return new HistoricDate(HistoricEra.AD, 1712, 2, 30);
            }
            return JULIAN.fromMJD(mjd + 1);
        }

        @Override
        boolean isValid(HistoricDate date) {
            if (
                (date.getDayOfMonth() == 30)
                && (date.getMonth() == 2)
                && (date.getYearOfEra() == 1712)
                && (date.getEra() == HistoricEra.AD)
            ) {
                return true;
            }
            return JulianMath.isValid(date.getYearOfEra(), date.getMonth(), date.getDayOfMonth());
        }

        @Override
        int getMaximumDayOfMonth(HistoricDate date) {
            if (
                (date.getMonth() == 2)
                && (date.getYearOfEra() == 1712)
                && (date.getEra() == HistoricEra.AD)
            ) {
                return 30;
            }
            return JulianMath.getLengthOfMonth(date.getYearOfEra(), date.getMonth());
        }
    };

    //~ Methoden ----------------------------------------------------------

    abstract long toMJD(HistoricDate date);

    abstract HistoricDate fromMJD(long mjd);

    abstract boolean isValid(HistoricDate date);

    abstract int getMaximumDayOfMonth(HistoricDate date);

    private static int getProlepticYear(HistoricDate date) {

        return ((date.getEra() == HistoricEra.AD) ? date.getYearOfEra() : 1 - date.getYearOfEra());

    }

}
