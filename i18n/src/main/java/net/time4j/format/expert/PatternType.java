/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (PatternType.java) is part of project Time4J.
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

import net.time4j.Moment;
import net.time4j.PlainDate;
import net.time4j.PlainTime;
import net.time4j.Weekmodel;
import net.time4j.engine.ChronoElement;
import net.time4j.engine.EpochDays;
import net.time4j.format.Attributes;
import net.time4j.format.ChronoPattern;
import net.time4j.format.DisplayMode;
import net.time4j.format.FormatEngine;
import net.time4j.format.OutputContext;
import net.time4j.format.TextElement;
import net.time4j.format.TextWidth;
import net.time4j.history.ChronoHistory;
import net.time4j.i18n.UltimateFormatEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * <p>Collection of different format patterns. </p>
 *
 * @author  Meno Hochschild
 * @since   3.0
 */
/*[deutsch]
 * <p>Sammlung von verschiedenen Standard-Formatmustern. </p>
 *
 * @author  Meno Hochschild
 * @since   3.0
 */
public enum PatternType
    implements ChronoPattern<PatternType> {

    //~ Statische Felder/Initialisierungen --------------------------------

    /**
     * <p>Follows the standard
     * <a href="http://www.unicode.org/reports/tr35/tr35-dates.html">LDML</a>
     * of unicode-consortium. </p>
     *
     * <p>If not explicitly stated otherwise the count of symbols always
     * controls the minimum count of digits in case of a numerical element.
     * Is an element shorter then the zero digit will be used for padding. </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legend</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Description</th>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#era() ERA}</td>
     *      <td>G</td>
     *      <td>One to three symbols indicate an abbreviation, four symbols
     *      indicate the long form and five symbols stand for a letter. The era
     *      is based on the chronological history of the current format locale.  </td>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#yearOfEra() YEAR_OF_ERA}</td>
     *      <td>y</td>
     *      <td>The count of symbols normally controls the minimum count of
     *      digits. If it is 2 however then the year will be printed with
     *      exact two digits using the attribute {@link Attributes#PIVOT_YEAR}.
     *      Important: If the era is not present then this symbol will simply
     *      be mapped to {@link PlainDate#YEAR}. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR_OF_WEEKDATE}</td>
     *      <td>Y</td>
     *      <td>Represents the year in an ISO-8601 week date and behaves
     *      like the calendar year in formatting. The week-based year can
     *      deviate from the calendar year however because it is bound to
     *      the week cycle. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR}</td>
     *      <td>u</td>
     *      <td>Proleptic ISO-8601 calendar year. This year never uses
     *      a pivot year, also not for &quot;uu&quot;. A positive sign
     *      will be used exactly if the year has more digits than given
     *      by count of symbols. In contrast to the symbol y, this year
     *      can never be the historized year-of-era. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#QUARTER_OF_YEAR}</td>
     *      <td>Q</td>
     *      <td>One or two symbols for the numerical form, three symbols
     *      for the abbreviation, four for the full name and five for
     *      a letter symbol (NARROW). </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#QUARTER_OF_YEAR}</td>
     *      <td>q</td>
     *      <td>Like Q, but in the version {@link OutputContext#STANDALONE}.
     *      In some languages (not english) the stand-alone-version requires
     *      a special grammar. </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>M</td>
     *      <td>One or two symbols for the numerical form, three symbols
     *      for the abbreviation, four for the full name and five for
     *      a letter symbol (NARROW). Important: If the era is not present
     *      then this symbol will simply be mapped to {@link PlainDate#MONTH_OF_YEAR}. </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>L</td>
     *      <td>Like M, but in the version {@link OutputContext#STANDALONE}.
     *      In some languages (not english) the stand-alone-version requires
     *      a special grammar. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#weekOfYear()}</td>
     *      <td>w</td>
     *      <td>One or two symbols for the country-dependent week of year. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#weekOfMonth()}</td>
     *      <td>W</td>
     *      <td>One symbol for the country-dependent week of month. </td>
     *  </tr>
     *  <tr>
     *      <td>DAY_OF_MONTH</td>
     *      <td>d</td>
     *      <td>One or two symbols for the day of month.  Important: If the era is not present
     *      then this symbol will simply be mapped to {@link PlainDate#DAY_OF_MONTH}. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_YEAR}</td>
     *      <td>D</td>
     *      <td>One, two or three symbols for the day of year. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#WEEKDAY_IN_MONTH}</td>
     *      <td>F</td>
     *      <td>One symbol for the weekday in month. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link EpochDays#MODIFIED_JULIAN_DATE}</td>
     *      <td>g</td>
     *      <td>The count of symbols usually controls the minimum count of
     *      digits of modified julian year, that is the count of days relative
     *      to 1858-11-17. Is only supported by calendrical types like
     *      {@code PlainDate}. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_WEEK}</td>
     *      <td>E</td>
     *      <td>One to three symbols for the abbreviation, four for the full
     *      name, five for a letter symbol or six for the short form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>e</td>
     *      <td>Like E, but if there are only one or two symbols then the
     *      formatter will choose the localized numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>c</td>
     *      <td>Like e, but in the version {@link OutputContext#STANDALONE}.
     *      In some languages (not english) the stand-alone-version requires
     *      a special grammar. However, 2 symbols are not allowed. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#AM_PM_OF_DAY}</td>
     *      <td>a</td>
     *      <td>One symbol for the text form. The attribute
     *      {@link Attributes#TEXT_WIDTH} can have impact on the length
     *      of the text form however. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#CLOCK_HOUR_OF_AMPM}</td>
     *      <td>h</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#DIGITAL_HOUR_OF_DAY}</td>
     *      <td>H</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#DIGITAL_HOUR_OF_AMPM}</td>
     *      <td>K</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#CLOCK_HOUR_OF_DAY}</td>
     *      <td>k</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MINUTE_OF_HOUR}</td>
     *      <td>m</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#SECOND_OF_MINUTE}</td>
     *      <td>s</td>
     *      <td>One or two symbols for the numerical form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_SECOND}</td>
     *      <td>S</td>
     *      <td>The count of symbols (1-9) controls the minimum and maximum
     *      count of digits to be printed. The decimal separation char will
     *      not be printed. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MILLI_OF_DAY}</td>
     *      <td>A</td>
     *      <td>The count of symbols (1-9) controls the minimum
     *      count of digits to be printed. </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_NAME</td>
     *      <td>z</td>
     *      <td>1-3 symbols for the abbreviation, 4 symbols for the full
     *      timezone name.</td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_OFFSET</td>
     *      <td>Z</td>
     *      <td>1-3 symbols =&gt; see xxxx, 4 symbols =&gt; see OOOO,
     *      5 symbols = &gt; see XXXXX.</td>
     *  </tr>
     *  <tr>
     *      <td>LOCALIZED_GMT_OFFSET</td>
     *      <td>O</td>
     *      <td>One symbol for the abbreviation or 4 symbols for the long
     *      variant. The GMT-prefix can also be available in a localized
     *      version from the resource file &quot;iso8601.properties&quot;,
     *      given the key &quot;prefixGMTOffset&quot;. </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_ID</td>
     *      <td>V</td>
     *      <td>The count of pattern symbols must always be 2. This symbol
     *      can only be applied on the type {@link Moment}. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>X</td>
     *      <td>One symbol: &#x00B1;HH[mm], two symbols: &#x00B1;HHmm, three
     *      symbols: &#x00B1;HH:mm, four symbols: &#x00B1;HHmm[ss[.{fraction}]],
     *      five symbols: &#x00B1;HH:mm[:ss[.{fraction}]]. If the timezone
     *      offset is equal to {@code 0} then the letter &quot;Z&quot; will
     *      be used. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>x</td>
     *      <td>Like X but without the special char &quot;Z&quot; if the
     *      timezone offset is equal to {@code 0}. </td>
     *  </tr>
     * </table>
     * </div>
     */
    /*[deutsch]
     * <p>Folgt der Norm
     * <a href="http://www.unicode.org/reports/tr35/tr35-dates.html">LDML</a>
     * des Unicode-Konsortiums. </p>
     *
     * <p>Wenn nicht explizit anders angegeben, steuert die Anzahl der Symbole
     * immer die minimale Anzahl der zu formatierenden Stellen, ein numerisches
     * Element vorausgesetzt. Ist also ein Element in der Darstellung
     * k&uuml;rzer, dann wird mit der Null-Ziffer aufgef&uuml;llt. </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legende</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Beschreibung</th>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#era() ERA}</td>
     *      <td>G</td>
     *      <td>Ein bis drei Symbole implizieren eine Abk&uuml;rzung, vier
     *      Symbole die Langform und f&uuml;nf Symbole stehen f&uuml;r ein
     *      Buchstabensymbol. Die &Auml;ra basiert auf dem Ausdruck
     *      {@code ChronoHistory.of(format-locale)}. </td>
     *  </tr>
     *  <tr>
     *      <td>YEAR_OF_ERA</td>
     *      <td>y</td>
     *      <td>Die Anzahl der Symbole regelt normalerweise die minimale
     *      Ziffernzahl. Ist sie jedoch 2, dann wird das Jahr zweistellig
     *      angezeigt - mit dem Attribut {@link Attributes#PIVOT_YEAR}.
     *      Wichtig: Ist die &Auml;ra nicht im Muster vorhanden, wird dieses
     *      Symbol dem Element {@link PlainDate#YEAR} zugeordnet. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR_OF_WEEKDATE}</td>
     *      <td>Y</td>
     *      <td>Entspricht dem Jahr in einem ISO-Wochendatum und verh&auml;lt
     *      sich in der Formatierung wie das Kalenderjahr. Das wochenbasierte
     *      Jahr kann im Wochenmodell des ISO-8601-Standards vom Kalenderjahr
     *      abweichen, weil es an den Wochenzyklus gebunden ist. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR}</td>
     *      <td>u</td>
     *      <td>Proleptisches ISO-Kalenderjahr. Diese Jahresangabe erfolgt
     *      nie mit Kippjahr, auch nicht f&uuml;r &quot;uu&quot;. Ein
     *      positives Vorzeichen wird genau dann ausgegeben, wenn das Jahr
     *      mehr Stellen hat als an Symbolen vorgegeben. Im Kontrast zum
     *      Symbol y kann dieses Jahr niemals das historische Jahr einer
     *      &Auml;ra sein. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#QUARTER_OF_YEAR}</td>
     *      <td>Q</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form, drei
     *      f&uuml;r die Abk&uuml;rzung, vier f&uuml;r den vollen Namen
     *      oder f&uuml;nf f&uuml;r ein Buchstabensymbol (NARROW).
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#QUARTER_OF_YEAR}</td>
     *      <td>q</td>
     *      <td>Wie Q, aber in der {@link OutputContext#STANDALONE
     *      Stand-Alone-Version}. In manchen Sprachen (nicht englisch)
     *      erfordert die alleinstehende Variante eine besondere
     *      Deklination. </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>M</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form, drei
     *      f&uuml;r die Abk&uuml;rzung, vier f&uuml;r den vollen Namen
     *      oder f&uuml;nf f&uuml;r ein Buchstabensymbol (NARROW).
     *      Wichtig: Ist die &Auml;ra nicht im Muster vorhanden, wird dieses
     *      Symbol dem Element {@link PlainDate#MONTH_OF_YEAR} zugeordnet. </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>L</td>
     *      <td>Wie M, aber in der {@link OutputContext#STANDALONE
     *      Stand-Alone-Version}. In manchen Sprachen (nicht englisch)
     *      erfordert die alleinstehende Variante eine besondere
     *      Deklination. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#weekOfYear()}</td>
     *      <td>w</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die l&auml;nderabh&auml;ngige
     *      Woche des Jahres. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#weekOfMonth()}</td>
     *      <td>W</td>
     *      <td>Ein Symbol f&uuml;r die l&auml;nderabh&auml;ngige
     *      Woche des Monats. </td>
     *  </tr>
     *  <tr>
     *      <td>DAY_OF_MONTH</td>
     *      <td>d</td>
     *      <td>Ein oder zwei Symbole f&uuml;r den Tag des Monats.
     *      Wichtig: Ist die &Auml;ra nicht im Muster vorhanden, wird dieses
     *      Symbol dem Element {@link PlainDate#DAY_OF_MONTH} zugeordnet.</td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_YEAR}</td>
     *      <td>D</td>
     *      <td>Ein, zwei oder drei Symbole f&uuml;r den Tag des Jahres. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#WEEKDAY_IN_MONTH}</td>
     *      <td>F</td>
     *      <td>Ein Symbol f&uuml;r den Wochentag im Monat. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link EpochDays#MODIFIED_JULIAN_DATE}</td>
     *      <td>g</td>
     *      <td>Die Anzahl der Symbole regelt wie &uuml;blich die minimale
     *      Anzahl der Stellen des modifizierten julianischen Jahres, also
     *      der Anzahl der Tage seit 1858-11-17. Wird nur von reinen
     *      Datumsklassen wie {@code PlainDate} unterst&uuml;tzt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_WEEK}</td>
     *      <td>E</td>
     *      <td>Ein bis drei Symbole f&uuml;r die Abk&uuml;rzung, vier
     *      f&uuml;r den vollen Namen, f&uuml;nf f&uuml;r ein Buchstabensymbol
     *      oder sechs f&uuml;r die Kurzform. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>e</td>
     *      <td>Wie E, aber wenn ein oder zwei Symbole angegeben sind, dann
     *      wird die lokalisierte numerische Form gew&auml;hlt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>c</td>
     *      <td>Wie e, aber in der {@link OutputContext#STANDALONE
     *      Stand-Alone-Version}. In manchen Sprachen (nicht englisch)
     *      erfordert die alleinstehende Variante eine besondere
     *      Deklination. Zu beachten: 2 Symbole sind nicht erlaubt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#AM_PM_OF_DAY}</td>
     *      <td>a</td>
     *      <td>Ein Symbol f&uuml;r die Textform. Das Attribut
     *      {@link Attributes#TEXT_WIDTH} kann die L&auml;nge der
     *      Textform beeinflussen. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#CLOCK_HOUR_OF_AMPM}</td>
     *      <td>h</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#DIGITAL_HOUR_OF_DAY}</td>
     *      <td>H</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#DIGITAL_HOUR_OF_AMPM}</td>
     *      <td>K</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#CLOCK_HOUR_OF_DAY}</td>
     *      <td>k</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MINUTE_OF_HOUR}</td>
     *      <td>m</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#SECOND_OF_MINUTE}</td>
     *      <td>s</td>
     *      <td>Ein oder zwei Symbole f&uuml;r die numerische Form. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_SECOND}</td>
     *      <td>S</td>
     *      <td>Die Anzahl der Symbole (1-9) regelt die minimale und maximale
     *      Anzahl der zu formatierenden Ziffern. Das Dezimaltrennzeichen
     *      wird nicht ausgegeben. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MILLI_OF_DAY}</td>
     *      <td>A</td>
     *      <td>Die Anzahl der Symbole (1-9) regelt die minimale Anzahl der
     *      zu formatierenden Ziffern. </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_NAME</td>
     *      <td>z</td>
     *      <td>1-3 Symbole f&uuml;r die Kurzform, 4 Symbole f&uuml;r den
     *      langen Zeitzonennamen.</td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_OFFSET</td>
     *      <td>Z</td>
     *      <td>1-3 Symbole =&gt; siehe xxxx, 4 Symbole =&gt; siehe OOOO,
     *      5 Symbole = &gt; siehe XXXXX.</td>
     *  </tr>
     *  <tr>
     *      <td>LOCALIZED_GMT_OFFSET</td>
     *      <td>O</td>
     *      <td>Ein Symbol f&uuml;r die Kurzform oder 4 Symbole f&uuml;r die
     *      Langform. Das GMT-Pr&auml;fix kann auch lokalisiert aus der
     *      &quot;iso8601.properties&quot;-Ressource stammen, zum Schl&uuml;ssel
     *      &quot;prefixGMTOffset&quot;. </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_ID</td>
     *      <td>V</td>
     *      <td>Es werden immer zwei Symbole erwartet. Dieses Symbol kann
     *      nur auf den Typ {@link Moment} angewandt werden. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>X</td>
     *      <td>Ein Symbol: &#x00B1;HH[mm], zwei Symbole: &#x00B1;HHmm, drei
     *      Symbole: &#x00B1;HH:mm, vier Symbole: &#x00B1;HHmm[ss[.{fraction}]],
     *      f&uuml;nf Symbole: &#x00B1;HH:mm[:ss[.{fraction}]]. Ist der
     *      Zeitzonen-Offset gleich {@code 0}, dann wird das Buchstabensymbol
     *      &quot;Z&quot; verwendet. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>x</td>
     *      <td>Wie X, aber ohne das Spezialzeichen &quot;Z&quot;, wenn der
     *      Zeitzonen-Offset gleich {@code 0} ist. </td>
     *  </tr>
     * </table>
     * </div>
     */
    CLDR,

    /**
     * <p>Follows the format pattern description of class
     * {@link java.text.SimpleDateFormat}, which is very near, but not
     * exactly the same as CLDR. </p>
     *
     * <p>The permitted count of digits is usually unlimited. Deviations
     * from {@link #CLDR}: </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legend</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Description</th>
     *  </tr>
     *  <tr>
     *      <td>ISO_DAY_OF_WEEK</td>
     *      <td>u</td>
     *      <td>Corresponds to the weekday-numbering of ISO-8601-standard
     *      ({@code Weekmodel.ISO.localDayOfWeek()}), that is:
     *      Mo=1, Di=2, Mi=3, Do=4, Fr=5, Sa=6, So=7. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#boundedWeekOfMonth()}</td>
     *      <td>W</td>
     *      <td>One symbol for the country-dependent week of month. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MILLI_OF_SECOND}</td>
     *      <td>S</td>
     *      <td>No fractional but only integral display of millisecond. </td>
     *  </tr>
     *  <tr>
     *      <td>QUARTER_OF_YEAR</td>
     *      <td>Q</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>QUARTER_OF_YEAR</td>
     *      <td>q</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>L</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>MODIFIED_JULIAN_DATE</td>
     *      <td>g</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>{local-day-of-week-number}</td>
     *      <td>e</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>{local-day-of-week-number}</td>
     *      <td>c</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>RFC_822_TIMEZONE_OFFSET</td>
     *      <td>Z</td>
     *      <td>Equivalent to CLDR-xx.</td>
     *  </tr>
     *  <tr>
     *      <td>LOCALIZED_GMT_OFFSET</td>
     *      <td>O</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_ID</td>
     *      <td>V</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>X</td>
     *      <td>Like in CLDR, but with only three symbols as upper limit. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>x</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     * </table>
     * </div>
     */
    /*[deutsch]
     * <p>Folgt der Formatmusterbeschreibung der Klasse
     * {@link java.text.SimpleDateFormat}, die sich stark, aber
     * nicht exakt an CLDR orientiert. </p>
     *
     * <p>Die erlaubte Anzahl der Symbole ist in der Regel nach oben offen.
     * Unterschiede zu {@link #CLDR}: </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legende</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Beschreibung</th>
     *  </tr>
     *  <tr>
     *      <td>ISO_DAY_OF_WEEK</td>
     *      <td>u</td>
     *      <td>Entspricht der Wochentagsnummerierung des
     *      ISO-8601-Formats ({@code Weekmodel.ISO.localDayOfWeek()}),
     *      also: Mo=1, Di=2, Mi=3, Do=4, Fr=5, Sa=6, So=7. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#boundedWeekOfMonth()}</td>
     *      <td>W</td>
     *      <td>Ein Symbol f&uuml;r die l&auml;nderabh&auml;ngige
     *      Woche des Monats. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#MILLI_OF_SECOND}</td>
     *      <td>S</td>
     *      <td>Keine fraktionale, sondern nur integrale Darstellung der
     *      Millisekunde. </td>
     *  </tr>
     *  <tr>
     *      <td>QUARTER_OF_YEAR</td>
     *      <td>Q</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>QUARTER_OF_YEAR</td>
     *      <td>q</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>MONTH_OF_YEAR</td>
     *      <td>L</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>MODIFIED_JULIAN_DATE</td>
     *      <td>g</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>{local-day-of-week-number}</td>
     *      <td>e</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>{local-day-of-week-number}</td>
     *      <td>c</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>RFC_822_TIMEZONE_OFFSET</td>
     *      <td>Z</td>
     *      <td>Entspricht CLDR-xx.</td>
     *  </tr>
     *  <tr>
     *      <td>LOCALIZED_GMT_OFFSET</td>
     *      <td>O</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>TIMEZONE_ID</td>
     *      <td>V</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>X</td>
     *      <td>Wie in CLDR, aber nur mit maximal drei Symbolen. </td>
     *  </tr>
     *  <tr>
     *      <td>ISO_TIMEZONE_OFFSET</td>
     *      <td>x</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     * </table>
     * </div>
     */
    SIMPLE_DATE_FORMAT,

    /**
     * <p>Follows the format pattern description of class
     * {@link java.time.format.DateTimeFormatter}, which is very near, but not
     * exactly the same as CLDR and extends it in some details. </p>
     *
     * <p>Deviations from {@link #CLDR}: </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legend</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Description</th>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#era() ERA}</td>
     *      <td>G</td>
     *      <td>Like in CLDR, but related to the proleptic gregorian calendar. It is NOT the historic era. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#yearOfEra() YEAR_OF_ERA}</td>
     *      <td>y</td>
     *      <td>Like in CLDR, but related to the proleptic gregorian calendar. It is NOT
     *      the historic year of era. Another important difference: It will use the pivot year 2100
     *      for the range 2000-2099 if the two-digit-form is choosen. If you want to configure the pivot
     *      year then use {@code PatternType.CLDR} instead. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR_OF_WEEKDATE}</td>
     *      <td>Y</td>
     *      <td>Like in CLDR, but the two-digit-form will use the pivot year 2100 for the range 2000-2099.
     *      If you want to configure the pivot year then use {@code PatternType.CLDR} instead. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR}</td>
     *      <td>u</td>
     *      <td>Like in CLDR, but the two-digit-form will use the pivot year 2100 for the range 2000-2099.
     *      If you want to configure the pivot year then use {@code PatternType.CLDR} instead. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link EpochDays#MODIFIED_JULIAN_DATE}</td>
     *      <td>g</td>
     *      <td>Not supported (as work-around: use CLDR). </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#boundedWeekOfMonth()}</td>
     *      <td>W</td>
     *      <td>One symbol for the country-dependent week of month. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_WEEK}</td>
     *      <td>E</td>
     *      <td>Like in CLDR, but no more than five pattern symbols are allowed. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>e</td>
     *      <td>Like in CLDR, but no more than five pattern symbols are allowed. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>c</td>
     *      <td>Like in CLDR, but no more than five pattern symbols are allowed. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_SECOND}</td>
     *      <td>n</td>
     *      <td>1-9 symbols allowed, no CLDR-equivalent. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_DAY}</td>
     *      <td>N</td>
     *      <td>1-18 symbols allowed, no CLDR-equivalent. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoFormatter.Builder#padNext(int)}</td>
     *      <td>p</td>
     *      <td>No CLDR-equivalent. </td>
     *  </tr>
     * </table>
     * </div>
     */
    /*[deutsch]
     * <p>Folgt der Formatmusterbeschreibung der Klasse
     * {@link java.time.format.DateTimeFormatter}, die sich stark, aber
     * nicht exakt an CLDR orientiert und letzteres in einigen Details erweitert. </p>
     *
     * <p>Unterschiede zu {@link #CLDR}: </p>
     *
     * <div style="margin-top:5px;">
     * <table border="1">
     *  <caption>Legende</caption>
     *  <tr>
     *      <th>Element</th>
     *      <th>Symbol</th>
     *      <th>Beschreibung</th>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#era() ERA}</td>
     *      <td>G</td>
     *      <td>Wie in CLDR, aber bezogen auf den proleptisch gregorianischen Kalender. Es handelt sich nicht
     *      um die historische &Auml;ra. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoHistory#yearOfEra() YEAR_OF_ERA}</td>
     *      <td>y</td>
     *      <td>Wie in CLDR, aber bezogen auf den proleptisch gregorianischen Kalender. Es handelt sich nicht
     *      um das historische Jahr der &Auml;ra. Ein anderer wichtiger Unterschied: Das Kippjahr 2100 f&uuml;r
     *      den Bereich 2000-2099 wird benutzt wenn eine zweistellige Jahresangabe gew&auml;hlt wird. Wenn das
     *      Kippjahr konfiguriert werden soll, dann ist {@code PatternType.CLDR} zu verwenden. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR_OF_WEEKDATE}</td>
     *      <td>Y</td>
     *      <td>Wie in CLDR, aber eine zweistellige Jahresangabe wird das Kippjahr 2100 f&uuml;r
     *      den Bereich 2000-2099 verwenden. Wenn das Kippjahr konfiguriert werden soll, ist
     *      {@code PatternType.CLDR} zu verwenden. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#YEAR}</td>
     *      <td>u</td>
     *      <td>Wie in CLDR, aber eine zweistellige Jahresangabe wird das Kippjahr 2100 f&uuml;r
     *      den Bereich 2000-2099 verwenden. Wenn das Kippjahr konfiguriert werden soll, ist
     *      {@code PatternType.CLDR} zu verwenden. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link EpochDays#MODIFIED_JULIAN_DATE}</td>
     *      <td>g</td>
     *      <td>Unterst&uuml;tzung nicht hier, sondern nur in CLDR. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#boundedWeekOfMonth()}</td>
     *      <td>W</td>
     *      <td>Ein Symbol f&uuml;r die l&auml;nderabh&auml;ngige
     *      Woche des Monats. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainDate#DAY_OF_WEEK}</td>
     *      <td>E</td>
     *      <td>Wie in CLDR, aber nicht mehr als 5 Symbole sind erlaubt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>e</td>
     *      <td>Wie in CLDR, aber nicht mehr als 5 Symbole sind erlaubt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link Weekmodel#localDayOfWeek()}</td>
     *      <td>c</td>
     *      <td>Wie in CLDR, aber nicht mehr als 5 Symbole sind erlaubt. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_SECOND}</td>
     *      <td>n</td>
     *      <td>1-9 Symbole erlaubt, kein CLDR-&Auml;quivalent. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link PlainTime#NANO_OF_DAY}</td>
     *      <td>N</td>
     *      <td>1-18 Symbole erlaubt, kein CLDR-&Auml;quivalent. </td>
     *  </tr>
     *  <tr>
     *      <td>{@link ChronoFormatter.Builder#padNext(int)}</td>
     *      <td>p</td>
     *      <td>Kein CLDR-&Auml;quivalent. </td>
     *  </tr>
     * </table>
     * </div>
     */
    THREETEN;

    //~ Methoden ----------------------------------------------------------

    @Override
    public FormatEngine<PatternType> getFormatEngine() {

        return UltimateFormatEngine.INSTANCE;

    }

    /**
     * <p>Registers a format symbol. </p>
     *
     * @param   builder     serves for construction of {@code ChronoFormatter}
     * @param   locale      current language- and country setting
     * @param   symbol      pattern symbol to be interpreted
     * @param   count       count of symbols in format pattern
     * @return  map of elements which will replace other already registered
     *          elements after pattern processing
     * @throws  IllegalArgumentException if symbol resolution fails
     */
    /*[deutsch]
     * <p>Registriert ein Formatsymbol. </p>
     *
     * @param   builder     serves for construction of {@code ChronoFormatter}
     * @param   locale      current language- and country setting
     * @param   symbol      pattern symbol to be interpreted
     * @param   count       count of symbols in format pattern
     * @return  map of elements which will replace other already registered
     *          elements after pattern processing
     * @throws  IllegalArgumentException if symbol resolution fails
     */
    Map<ChronoElement<?>, ChronoElement<?>> registerSymbol(
        ChronoFormatter.Builder<?> builder,
        Locale locale,
        char symbol,
        int count
    ) {

        switch (this) {
            case CLDR:
                return cldr(builder, locale, symbol, count, false);
            case SIMPLE_DATE_FORMAT:
                return sdf(builder, locale, symbol, count);
            case THREETEN:
                return threeten(builder, locale, symbol, count);
            default:
                throw new UnsupportedOperationException(this.name());
        }

    }

    private Map<ChronoElement<?>, ChronoElement<?>> cldr(
        ChronoFormatter.Builder<?> builder,
        Locale locale,
        char symbol,
        int count,
        boolean sdf
    ) {

        switch (symbol) {
            case 'G':
                TextWidth eraWidth;
                if (count <= 3) {
                    eraWidth = TextWidth.ABBREVIATED;
                } else if ((count == 4) || sdf) {
                    eraWidth = TextWidth.WIDE;
                } else if (count == 5) {
                    eraWidth = TextWidth.NARROW;
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                builder.startSection(Attributes.TEXT_WIDTH, eraWidth);
                ChronoHistory history = ChronoHistory.of(locale);
                TextElement<?> eraElement = history.era();
                builder.addText(eraElement);
                builder.endSection();
                Map<ChronoElement<?>, ChronoElement<?>> replacement = new HashMap<>();
                replacement.put(PlainDate.YEAR, history.yearOfEra());
                replacement.put(PlainDate.MONTH_OF_YEAR, history.month());
                replacement.put(PlainDate.MONTH_AS_NUMBER, history.month());
                replacement.put(PlainDate.DAY_OF_MONTH, history.dayOfMonth());
                return replacement;
            case 'y':
                if (count == 2) {
                    builder.addTwoDigitYear(PlainDate.YEAR);
                } else if (count < 4) {
                    builder.addInteger(
                        PlainDate.YEAR,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addInteger(
                        PlainDate.YEAR,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'Y':
                if (count == 2) {
                    builder.addTwoDigitYear(PlainDate.YEAR_OF_WEEKDATE);
                } else if (count < 4) {
                    builder.addInteger(
                        PlainDate.YEAR_OF_WEEKDATE,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addInteger(
                        PlainDate.YEAR_OF_WEEKDATE,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'u':
                if (count < 4) {
                    builder.addProlepticIsoYear(
                        count,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addProlepticIsoYear(
                        count,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'Q':
                addQuarterOfYear(builder, count);
                break;
            case 'q':
                builder.startSection(
                    Attributes.OUTPUT_CONTEXT, OutputContext.STANDALONE);
                try {
                    addQuarterOfYear(builder, count);
                } finally {
                    builder.endSection();
                }
                break;
            case 'M':
                addMonth(builder, Math.min(count, sdf ? 4 : count));
                break;
            case 'L':
                builder.startSection(
                    Attributes.OUTPUT_CONTEXT, OutputContext.STANDALONE);
                try {
                    addMonth(builder, count);
                } finally {
                    builder.endSection();
                }
                break;
            case 'w':
                addNumber(
                    Weekmodel.of(locale).weekOfYear(), builder, count, sdf);
                break;
            case 'W':
                if (count == 1) {
                    builder.addFixedInteger(
                        Weekmodel.of(locale).weekOfMonth(), 1);
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'd':
                addNumber(PlainDate.DAY_OF_MONTH, builder, count, sdf);
                break;
            case 'D':
                if (count < 3) {
                    builder.addInteger(PlainDate.DAY_OF_YEAR, count, 3);
                } else if ((count == 3) || sdf) {
                    builder.addFixedInteger(PlainDate.DAY_OF_YEAR, count);
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'F':
                if ((count == 1) || sdf) {
                    builder.addFixedInteger(PlainDate.WEEKDAY_IN_MONTH, count);
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'g':
                builder.addLongNumber(
                    EpochDays.MODIFIED_JULIAN_DATE,
                    count,
                    18,
                    SignPolicy.SHOW_WHEN_NEGATIVE);
                break;
            case 'E':
                TextWidth width;
                if (count <= 3) {
                    width = TextWidth.ABBREVIATED;
                } else if ((count == 4) || sdf) {
                    width = TextWidth.WIDE;
                } else if (count == 5) {
                    width = TextWidth.NARROW;
                } else if (count == 6) {
                    width = TextWidth.SHORT;
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                builder.startSection(Attributes.TEXT_WIDTH, width);
                builder.addText(PlainDate.DAY_OF_WEEK);
                builder.endSection();
                break;
            case 'e':
                if (count <= 2) {
                    builder.addFixedNumerical(
                        Weekmodel.of(locale).localDayOfWeek(), count);
                } else {
                    cldr(builder, locale, 'E', count, sdf);
                }
                break;
            case 'c':
                if (count == 2) {
                    throw new IllegalArgumentException(
                        "Invalid pattern count of 2 for symbol 'c'.");
                }
                builder.startSection(
                    Attributes.OUTPUT_CONTEXT, OutputContext.STANDALONE);
                try {
                    if (count == 1) {
                        builder.addFixedNumerical(
                            Weekmodel.of(locale).localDayOfWeek(), 1);
                    } else {
                        cldr(builder, locale, 'E', count, sdf);
                    }
                } finally {
                    builder.endSection();
                }
                break;
            case 'a':
                if ((count == 1) || sdf) {
                    builder.addText(PlainTime.AM_PM_OF_DAY);
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'h':
                addNumber(PlainTime.CLOCK_HOUR_OF_AMPM, builder, count, sdf);
                break;
            case 'H':
                addNumber(PlainTime.DIGITAL_HOUR_OF_DAY, builder, count, sdf);
                break;
            case 'K':
                addNumber(PlainTime.DIGITAL_HOUR_OF_AMPM, builder, count, sdf);
                break;
            case 'k':
                addNumber(PlainTime.CLOCK_HOUR_OF_DAY, builder, count, sdf);
                break;
            case 'm':
                addNumber(PlainTime.MINUTE_OF_HOUR, builder, count, sdf);
                break;
            case 's':
                addNumber(PlainTime.SECOND_OF_MINUTE, builder, count, sdf);
                break;
            case 'S':
                builder.addFraction(
                    PlainTime.NANO_OF_SECOND, count, count, false);
                break;
            case 'A':
                builder.addInteger(PlainTime.MILLI_OF_DAY, count, 9);
                break;
            case 'z':
                if (count < 4) {
                    builder.addShortTimezoneName();
                } else if ((count == 4) || sdf) {
                    builder.addLongTimezoneName();
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'Z':
                if (count < 4) {
                    builder.addTimezoneOffset(
                        DisplayMode.LONG,
                        false,
                        Collections.singletonList("+0000"));
                } else if (count == 4) {
                    builder.addLongLocalizedOffset();
                } else if (count == 5) {
                    builder.addTimezoneOffset(
                        DisplayMode.LONG,
                        true,
                        Collections.singletonList("Z"));
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'O':
                if (count == 1) {
                    builder.addShortLocalizedOffset();
                } else if (count == 4) {
                    builder.addLongLocalizedOffset();
                } else {
                    throw new IllegalArgumentException(
                        "Count of pattern letters is not 1 or 4: " + count);
                }
                break;
            case 'V':
                if (count == 2) {
                    try {
                        builder.addTimezoneID();
                    } catch (IllegalStateException ise) {
                        throw new IllegalArgumentException(ise.getMessage());
                    }
                } else {
                    throw new IllegalArgumentException(
                        "Count of pattern letters is not 2: " + count);
                }
                break;
            case 'X':
                addOffset(builder, count, true);
                break;
            case 'x':
                addOffset(builder, count, false);
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported pattern symbol: " + symbol);
        }

        return Collections.emptyMap();

    }

    private Map<ChronoElement<?>, ChronoElement<?>> sdf(
        ChronoFormatter.Builder<?> builder,
        Locale locale,
        char symbol,
        int count
    ) {

        switch (symbol) {
            case 'W':
                builder.addFixedInteger(
                    Weekmodel.of(locale).boundedWeekOfMonth(),
                    count);
                break;
            case 'u':
                builder.addFixedNumerical(PlainDate.DAY_OF_WEEK, count);
                break;
            case 'S':
                builder.addFixedInteger(PlainTime.MILLI_OF_SECOND, count);
                break;
            case 'Z':
                addOffset(builder, 2, false);
                break;
            case 'Q':
            case 'q':
            case 'L':
            case 'g':
            case 'e':
            case 'c':
            case 'O':
            case 'V':
            case 'x':
                throw new IllegalArgumentException(
                    "CLDR pattern symbol not supported"
                    + " in SimpleDateFormat-style: "
                    + symbol);
            case 'X':
                if (count >= 4) {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                return cldr(builder, locale, 'X', count, true);
            default:
                return cldr(builder, locale, symbol, count, true);
        }

        return Collections.emptyMap();

    }

    private Map<ChronoElement<?>, ChronoElement<?>> threeten(
        ChronoFormatter.Builder<?> builder,
        Locale locale,
        char symbol,
        int count
    ) {

        switch (symbol) {
            case 'G':
                TextWidth eraWidth;
                if (count <= 3) {
                    eraWidth = TextWidth.ABBREVIATED;
                } else if (count == 4) {
                    eraWidth = TextWidth.WIDE;
                } else if (count == 5) {
                    eraWidth = TextWidth.NARROW;
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                builder.startSection(Attributes.TEXT_WIDTH, eraWidth);
                ChronoHistory history = ChronoHistory.PROLEPTIC_GREGORIAN;
                TextElement<?> eraElement = history.era();
                builder.addText(eraElement);
                builder.endSection();
                builder.setProlepticGregorian();
                Map<ChronoElement<?>, ChronoElement<?>> replacement = new HashMap<>();
                replacement.put(PlainDate.YEAR, history.yearOfEra());
                replacement.put(PlainDate.MONTH_OF_YEAR, history.month());
                replacement.put(PlainDate.MONTH_AS_NUMBER, history.month());
                replacement.put(PlainDate.DAY_OF_MONTH, history.dayOfMonth());
                return replacement;
            case 'y':
                if (count == 2) {
                    builder.startSection(Attributes.PIVOT_YEAR, 2100);
                    builder.addTwoDigitYear(PlainDate.YEAR);
                    builder.endSection();
                } else if (count < 4) {
                    builder.addInteger(
                        PlainDate.YEAR,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addInteger(
                        PlainDate.YEAR,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'Y':
                if (count == 2) {
                    builder.startSection(Attributes.PIVOT_YEAR, 2100);
                    builder.addTwoDigitYear(PlainDate.YEAR_OF_WEEKDATE);
                    builder.endSection();
                } else if (count < 4) {
                    builder.addInteger(
                        PlainDate.YEAR_OF_WEEKDATE,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addInteger(
                        PlainDate.YEAR_OF_WEEKDATE,
                        count,
                        9,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'u':
                if (count == 2) {
                    builder.startSection(Attributes.PIVOT_YEAR, 2100);
                    builder.addProlepticIsoYearWithTwoDigits();
                    builder.endSection();
                } else if (count < 4) {
                    builder.addProlepticIsoYear(
                        count,
                        SignPolicy.SHOW_WHEN_NEGATIVE);
                } else {
                    builder.addProlepticIsoYear(
                        count,
                        SignPolicy.SHOW_WHEN_BIG_NUMBER);
                }
                break;
            case 'W':
                if (count == 1) {
                    builder.addFixedInteger(
                        Weekmodel.of(locale).boundedWeekOfMonth(), 1);
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'E':
                TextWidth width;
                if (count <= 3) {
                    width = TextWidth.ABBREVIATED;
                } else if (count == 4) {
                    width = TextWidth.WIDE;
                } else if (count == 5) {
                    width = TextWidth.NARROW;
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                builder.startSection(Attributes.TEXT_WIDTH, width);
                builder.addText(PlainDate.DAY_OF_WEEK);
                builder.endSection();
                break;
            case 'e':
                if (count <= 2) {
                    builder.addFixedNumerical(
                        Weekmodel.of(locale).localDayOfWeek(), count);
                } else {
                    threeten(builder, locale, 'E', count);
                }
                break;
            case 'c':
                if (count == 2) {
                    throw new IllegalArgumentException(
                        "Invalid pattern count of 2 for symbol 'c'.");
                }
                builder.startSection(
                    Attributes.OUTPUT_CONTEXT, OutputContext.STANDALONE);
                try {
                    threeten(builder, locale, 'e', count);
                } finally {
                    builder.endSection();
                }
                break;
            case 'w':
            case 'Q':
            case 'q':
            case 'M':
            case 'L':
            case 'd':
            case 'D':
            case 'F':
            case 'a':
            case 'h':
            case 'H':
            case 'K':
            case 'k':
            case 'm':
            case 's':
            case 'S':
            case 'A':
                return cldr(builder, locale, symbol, count, false);
            case 'n':
                builder.addInteger(PlainTime.NANO_OF_SECOND, count, 9);
                break;
            case 'N':
                builder.addLongNumber(PlainTime.NANO_OF_DAY, count, 18, SignPolicy.SHOW_NEVER);
                break;
            case 'z':
                return cldr(builder, locale, 'z', count, false);
            case 'Z':
                if (count < 4) {
                    builder.addTimezoneOffset(
                        DisplayMode.MEDIUM,
                        false,
                        Collections.singletonList("+0000"));
                } else if (count == 4) {
                    builder.addLongLocalizedOffset();
                } else if (count == 5) {
                    builder.addTimezoneOffset(
                        DisplayMode.LONG,
                        true,
                        Collections.singletonList("Z"));
                } else {
                    throw new IllegalArgumentException(
                        "Too many pattern letters: " + count);
                }
                break;
            case 'O':
            case 'V':
            case 'X':
            case 'x':
                return cldr(builder, locale, symbol, count, false);
            case 'p':
                builder.padNext(count);
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported pattern symbol: " + symbol);
        }

        return Collections.emptyMap();

    }

    private static void addOffset(
        ChronoFormatter.Builder<?> builder,
        int count,
        boolean zulu
    ) {

        switch (count) {
            case 1:
                builder.addTimezoneOffset(
                    DisplayMode.SHORT,
                    false,
                    Collections.singletonList(zulu ? "Z" : "+00"));
                break;
            case 2:
                builder.addTimezoneOffset(
                    DisplayMode.MEDIUM,
                    false,
                    Collections.singletonList(zulu ? "Z" : "+0000"));
                break;
            case 3:
                builder.addTimezoneOffset(
                    DisplayMode.MEDIUM,
                    true,
                    Collections.singletonList(zulu ? "Z" : "+00:00"));
                break;
            case 4:
                builder.addTimezoneOffset(
                    DisplayMode.LONG,
                    false,
                    Collections.singletonList(zulu ? "Z" : "+0000"));
                break;
            case 5:
                builder.addTimezoneOffset(
                    DisplayMode.LONG,
                    true,
                    Collections.singletonList(zulu ? "Z" : "+00:00"));
                break;
            default:
                throw new IllegalArgumentException(
                    "Too many pattern letters: " + count);
        }

    }

    private static void addNumber(
        ChronoElement<Integer> element,
        ChronoFormatter.Builder<?> builder,
        int count,
        boolean sdf
    ) {

        if (count == 1) {
            builder.addInteger(element, 1, 2);
        } else if ((count == 2) || sdf) {
            builder.addFixedInteger(element, count);
        } else {
            throw new IllegalArgumentException(
                "Too many pattern letters: " + count);
        }

    }

    private static void addMonth(
        ChronoFormatter.Builder<?> builder,
        int count
    ) {

        switch (count) {
            case 1:
                builder.addInteger(PlainDate.MONTH_AS_NUMBER, 1, 2);
                break;
            case 2:
                builder.addFixedInteger(PlainDate.MONTH_AS_NUMBER, 2);
                break;
            case 3:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.ABBREVIATED);
                builder.addText(PlainDate.MONTH_OF_YEAR);
                builder.endSection();
                break;
            case 4:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.WIDE);
                builder.addText(PlainDate.MONTH_OF_YEAR);
                builder.endSection();
                break;
            case 5:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.NARROW);
                builder.addText(PlainDate.MONTH_OF_YEAR);
                builder.endSection();
                break;
            default:
                throw new IllegalArgumentException(
                    "Too many pattern letters: " + count);
        }

    }

    private static void addQuarterOfYear(
        ChronoFormatter.Builder<?> builder,
        int count
    ) {

        switch (count) {
            case 1:
            case 2:
                builder.addFixedNumerical(PlainDate.QUARTER_OF_YEAR, count);
                break;
            case 3:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.ABBREVIATED);
                builder.addText(PlainDate.QUARTER_OF_YEAR);
                builder.endSection();
                break;
            case 4:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.WIDE);
                builder.addText(PlainDate.QUARTER_OF_YEAR);
                builder.endSection();
                break;
            case 5:
                builder.startSection(
                    Attributes.TEXT_WIDTH, TextWidth.NARROW);
                builder.addText(PlainDate.QUARTER_OF_YEAR);
                builder.endSection();
                break;
            default:
                throw new IllegalArgumentException(
                    "Too many pattern letters: " + count);
        }

    }

}
