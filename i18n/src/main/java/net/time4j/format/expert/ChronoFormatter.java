/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (ChronoFormatter.java) is part of project Time4J.
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
import net.time4j.PlainTimestamp;
import net.time4j.base.UnixTime;
import net.time4j.engine.AttributeKey;
import net.time4j.engine.AttributeQuery;
import net.time4j.engine.ChronoCondition;
import net.time4j.engine.ChronoDisplay;
import net.time4j.engine.ChronoElement;
import net.time4j.engine.ChronoEntity;
import net.time4j.engine.ChronoException;
import net.time4j.engine.ChronoExtension;
import net.time4j.engine.ChronoFunction;
import net.time4j.engine.Chronology;
import net.time4j.engine.TimeAxis;
import net.time4j.engine.ValidationElement;
import net.time4j.format.Attributes;
import net.time4j.format.DisplayMode;
import net.time4j.format.Leniency;
import net.time4j.format.NumericalElement;
import net.time4j.format.OutputContext;
import net.time4j.format.PluralCategory;
import net.time4j.format.RawValues;
import net.time4j.format.TemporalFormatter;
import net.time4j.format.TextElement;
import net.time4j.format.TextWidth;
import net.time4j.history.ChronoHistory;
import net.time4j.history.HistoricVariant;
import net.time4j.tz.TZID;
import net.time4j.tz.Timezone;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.time4j.format.CalendarText.ISO_CALENDAR_TYPE;


/**
 * <p>Represents a chronological format for the conversion between a
 * chronological text and the chronological value of type T. </p>
 *
 * <p>An instance can either be created via a {@code Builder} obtainable
 * by {@link #setUp(Class, Locale)} or by some predefined format constants
 * in {@link Iso8601Format} which can be adjusted by some
 * {@code with()}-methods. Another way to create an instance are the
 * methods {@code formatter(...)} and {@code localFormatter(...)} in
 * the classes {@code PlainDate}, {@code PlainTime}, {@code PlainTimestamp}
 * and {@code Moment}. </p>
 *
 * @param   <T> generic type of chronological entity
 * @author  Meno Hochschild
 * @since   3.0
 * @doctags.concurrency {immutable}
 */
/*[deutsch]
 * <p>Repr&auml;sentiert ein Zeitformat zur Konversion zwischen einem
 * chronologischen Text und einem chronologischen Wert des Typs T. </p>
 *
 * <p>Eine Instanz kann entweder &uuml;ber einen {@code Builder} via
 * {@link #setUp(Class, Locale)} erzeugt werden, oder die Hauptpaket-Klasse
 * {@link Iso8601Format} liefert einige vordefinierte Formate,
 * die dann mit den {@code with()}-Methoden geeignet angepasst werden
 * k&ouml;nnen. Ein anderer Weg sind die Methoden {@code formatter(...)}
 * und {@code localFormatter(...)} der Klassen {@code PlainDate},
 * {@code PlainTime}, {@code PlainTimestamp} und {@code Moment}. </p>
 *
 * @param   <T> generic type of chronological entity
 * @author  Meno Hochschild
 * @since   3.0
 * @doctags.concurrency {immutable}
 */
public final class ChronoFormatter<T extends ChronoEntity<T>>
    implements ChronoPrinter<T>, ChronoParser<T>, TemporalFormatter<T> {

    //~ Instanzvariablen --------------------------------------------------

    private final Chronology<T> chronology;
    private final AttributeSet globalAttributes;
    private final List<FormatStep> steps;
    private final Map<ChronoElement<?>, Object> defaults;
    private final FractionProcessor fracproc;

    //~ Konstruktoren -----------------------------------------------------

    // Aufruf durch Builder
    private ChronoFormatter(
        Chronology<T> chronology,
        Locale locale,
        List<FormatStep> steps
    ) {
        super();

        if (chronology == null) {
            throw new NullPointerException("Missing chronology.");
        } else if (steps.isEmpty()) {
            throw new IllegalArgumentException(
                "No format processors defined.");
        }

        this.chronology = chronology;
        this.globalAttributes = AttributeSet.createDefaults(chronology, locale);
        this.steps = Collections.unmodifiableList(steps);
        this.defaults = Collections.emptyMap();

        FractionProcessor fp = null;

        for (FormatStep step : this.steps) {
            if (step.getProcessor() instanceof FractionProcessor) {
                fp = FractionProcessor.class.cast(step.getProcessor());
                break;
            }
        }

        this.fracproc = fp;

    }

    // Aufruf durch withAttribute-Methoden
    private ChronoFormatter(
        ChronoFormatter<T> old,
        Attributes attrs
    ) {
        this(old, old.globalAttributes.withAttributes(attrs), null);

    }

    // Aufruf durch with(Locale)-Methode
    private ChronoFormatter(
        ChronoFormatter<T> old,
        AttributeSet globalAttributes
    ) {
        this(old, globalAttributes, null);

    }

    // Aufruf durch withGregorianCutOver
    private ChronoFormatter(
        ChronoFormatter<T> old,
        AttributeSet globalAttributes,
        ChronoHistory history
    ) {
        super();

        if (globalAttributes == null) {
            throw new NullPointerException("Missing global format attributes.");
        }

        this.chronology = old.chronology;
        this.globalAttributes = globalAttributes;
        this.defaults =
            Collections.unmodifiableMap(
                new NonAmbivalentMap(old.defaults));
        this.fracproc = old.fracproc;

        // update extension elements and historizable elements
        int len = old.steps.size();
        List<FormatStep> copy = new ArrayList<>(old.steps);

        for (int i = 0; i < len; i++) {
            FormatStep step = copy.get(i);
            ChronoElement<?> element = step.getProcessor().getElement();

            // update extension elements
            if (
                (element != null) // no literal steps etc.
                && !this.chronology.isRegistered(element)
            ) {
                // example: week-of-year dependent on LOCALE
                for (ChronoExtension ext : this.chronology.getExtensions()) {
                    if (ext.getElements(old.getLocale(), old.globalAttributes).contains(element)) {
                        Set<ChronoElement<?>> elements =
                            ext.getElements(globalAttributes.getLocale(), globalAttributes);

                        for (ChronoElement<?> e : elements) {
                            if (e.name().equals(element.name())) {
                                if (e != element) {
                                    copy.set(i, step.updateElement(e));
                                }
                                break;
                            }
                        }

                        break;
                    }
                }
            }

            // update historizable elements
            if (history != null) {
                ChronoElement<?> replacement = null;
                if (element == PlainDate.YEAR) {
                    replacement = history.yearOfEra();
                } else if (
                    (element == PlainDate.MONTH_OF_YEAR)
                    || (element == PlainDate.MONTH_AS_NUMBER)
                ) {
                    replacement = history.month();
                } else if (element == PlainDate.DAY_OF_MONTH) {
                    replacement = history.dayOfMonth();
                }
                if (replacement != null) {
                    copy.set(i, step.updateElement(replacement));
                }
            }
        }

        this.steps = Collections.unmodifiableList(copy);

    }

    // Aufruf durch withDefault
    private ChronoFormatter(
        ChronoFormatter<T> formatter,
        ChronoElement<?> element,
        Object replacement
    ) {
        super();

        if (element == null) {
            throw new NullPointerException("Missing element.");
        }

        checkElement(formatter.chronology, element);

        this.chronology = formatter.chronology;
        this.globalAttributes = formatter.globalAttributes;
        this.fracproc = formatter.fracproc;

        Map<ChronoElement<?>, Object> map =
            new NonAmbivalentMap(formatter.defaults);

        if (replacement == null) {
            map.remove(element);
        } else {
            map.put(element, replacement);
        }

        this.defaults = Collections.unmodifiableMap(map);

        List<FormatStep> copy = new ArrayList<>(formatter.steps);
        this.steps = Collections.unmodifiableList(copy);

    }

    //~ Methoden ----------------------------------------------------------

    /**
     * <p>Returns the associated chronology. </p>
     *
     * @return  chronology to be used for formatting associated objects
     */
    /*[deutsch]
     * <p>Ermittelt die zugeh&ouml;rige Chronologie. </p>
     *
     * @return  chronology to be used for formatting associated objects
     */
    public Chronology<T> getChronology() {

        return this.chronology;

    }

    /**
     * <p>Returns the locale setting. </p>
     *
     * <p>If there is just a reference to ISO-8601 without any concrete
     * language then this method will yield {@code Locale.ROOT}. </p>
     *
     * @return  Locale (empty if related to ISO-8601, never {@code null})
     */
    /*[deutsch]
     * <p>Ermittelt die Sprach- und L&auml;ndereinstellung. </p>
     *
     * <p>Falls ein Bezug zu ISO-8601 ohne eine konkrete Sprache vorliegt,
     * liefert die Methode {@code Locale.ROOT}. </p>
     *
     * @return  Locale (empty if related to ISO-8601, never {@code null})
     */
    public Locale getLocale() {

        return this.globalAttributes.getLocale();

    }

    /**
     * <p>Returns the global format attributes which are active if they are not
     * overridden by sectional attributes. </p>
     *
     * <p>These attributes can be adjusted by a suitable
     * {@code with()}-method. Following attributes are predefined: </p>
     *
     * <table border="1" style="margin-top:5px;">
     *  <caption>Legend</caption>
     *  <tr>
     *      <td>{@link Attributes#CALENDAR_TYPE}</td>
     *      <td>dependent on associated chronology</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#LANGUAGE}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#DECIMAL_SEPARATOR}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#ZERO_DIGIT}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#LENIENCY}</td>
     *      <td>{@link Leniency#SMART}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PARSE_CASE_INSENSITIVE}</td>
     *      <td>{@code true}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PARSE_PARTIAL_COMPARE}</td>
     *      <td>{@code false}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#TEXT_WIDTH}</td>
     *      <td>{@link TextWidth#WIDE}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#OUTPUT_CONTEXT}</td>
     *      <td>{@link OutputContext#FORMAT}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PAD_CHAR}</td>
     *      <td>(SPACE)</td>
     *  </tr>
     * </table>
     *
     * @return  global control attributes valid for the whole formatter
     *          (can be overridden by sectional attributes however)
     * @see     #getChronology()
     * @see     #getLocale()
     */
    /*[deutsch]
     * <p>Ermittelt die globalen Standardattribute, welche genau dann wirksam sind,
     * wenn sie nicht durch sektionale Attribute &uuml;berschrieben werden. </p>
     *
     * <p>Die Standard-Attribute k&ouml;nnen &uuml;ber eine geeignete
     * {@code with()}-Methode ge&auml;ndert werden. Folgende Attribute
     * werden vordefiniert: </p>
     *
     * <table border="1" style="margin-top:5px;">
     *  <caption>Legende</caption>
     *  <tr>
     *      <td>{@link Attributes#CALENDAR_TYPE}</td>
     *      <td>dependent on associated chronology</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#LANGUAGE}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#DECIMAL_SEPARATOR}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#ZERO_DIGIT}</td>
     *      <td>dependent on associated locale</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#LENIENCY}</td>
     *      <td>{@link Leniency#SMART}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PARSE_CASE_INSENSITIVE}</td>
     *      <td>{@code true}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PARSE_PARTIAL_COMPARE}</td>
     *      <td>{@code false}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#TEXT_WIDTH}</td>
     *      <td>{@link TextWidth#WIDE}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#OUTPUT_CONTEXT}</td>
     *      <td>{@link OutputContext#FORMAT}</td>
     *  </tr>
     *  <tr>
     *      <td>{@link Attributes#PAD_CHAR}</td>
     *      <td>Leerzeichen (SPACE)</td>
     *  </tr>
     * </table>
     *
     * @return  global control attributes valid for the whole formatter
     *          (can be overridden by sectional attributes however)
     * @see     #getChronology()
     * @see     #getLocale()
     */
    @Override
    public AttributeQuery getAttributes() {

        return this.globalAttributes;

    }

    @Override
    public String format(T formattable) {

        ChronoDisplay display = this.chronology.preformat(formattable, this.globalAttributes);
        StringBuilder buffer = new StringBuilder(this.steps.size() * 8);

        try {
            this.print(display, buffer, this.globalAttributes, false);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe); // cannot happen
        }

        return buffer.toString();

    }

    /**
     * <p>Prints the given Threeten-object. </p>
     *
     * @param   formattable     temporal accessor
     * @return  formatted text
     * @throws  IllegalArgumentException if given argument cannot be formatted
     * @since   4.0
     */
    /*[deutsch]
     * <p>Formatiert das angegebene Threeten-Objekt. </p>
     *
     * @param   formattable     temporal accessor
     * @return  formatted text
     * @throws  IllegalArgumentException if given argument cannot be formatted
     * @since   4.0
     */
    public String format(TemporalAccessor formattable) {

        T entity = this.toEntity(formattable);
        return this.toZonedFormatter(formattable).format(entity);

    }

    @Override
    public void formatToBuffer(
        T formattable,
        Appendable buffer
    ) throws IOException {

        this.print(formattable, buffer, this.globalAttributes);

    }


    /**
     * <p>Prints given chronological entity as formatted text and writes
     * the text into given buffer. </p>
     *
     * <p>Equivalent to {@code print(formattable, buffer, getAttributes())}. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     */
    /*[deutsch]
     * <p>Formatiert das angegebene Objekt als Text und schreibt ihn in
     * den Puffer. </p>
     *
     * <p>Entspricht {@code print(formattable, buffer, getAttributes())}. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     */
    public Set<ElementPosition> print(
        T formattable,
        StringBuilder buffer
    ) {

        ChronoDisplay display =
            this.chronology.preformat(formattable, this.globalAttributes);

        try {
            return this.print(display, buffer, this.globalAttributes, true);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe); // cannot happen
        }

    }

    /**
     * <p>Prints given Threeten-object as formatted text and writes
     * the text into given buffer. </p>
     *
     * <p>Similar to {@code print(formattable, buffer, getAttributes())}. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     * @throws  IOException if writing to buffer fails
     * @since   4.0
     */
    /*[deutsch]
     * <p>Formatiert das angegebene Threeten-Objekt als Text und schreibt ihn in
     * den Puffer. </p>
     *
     * <p>&Auml;hnlich zu {@code print(formattable, buffer, getAttributes())}. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     * @throws  IOException if writing to buffer fails
     * @since   4.0
     */
    public Set<ElementPosition> print(
        TemporalAccessor formattable,
        Appendable buffer
    ) throws IOException {

        T entity = this.toEntity(formattable);
        return this.toZonedFormatter(formattable).print(entity, buffer, this.globalAttributes);

    }

    /**
     * <p>Prints given chronological entity as formatted text and writes
     * the text into given buffer. </p>
     *
     * <p>The given attributes cannot change the inner format structure
     * (for example not change a localized weekmodel), but can override some
     * format properties like language or certain text attributes for this
     * run only. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @param   attributes      attributes for limited formatting control
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     * @throws  IOException if writing to buffer fails
     */
    /*[deutsch]
     * <p>Erzeugt eine Textausgabe und speichert sie im angegebenen Puffer. </p>
     *
     * <p>Die mitgegebenen Steuerattribute k&ouml;nnen nicht die innere
     * Formatstruktur &auml;ndern (zum Beispiel nicht ein lokalisiertes
     * Wochenmodell wechseln), aber bestimmte Formateigenschaften wie
     * die Sprachausgabe oder Textattribute individuell nur f&uuml;r diesen
     * Lauf setzen. </p>
     *
     * @param   formattable     object to be formatted
     * @param   buffer          text output buffer
     * @param   attributes      attributes for limited formatting control
     * @return  unmodifiable set of element positions in formatted text
     * @throws  IllegalArgumentException if given object is not formattable
     * @throws  IOException if writing to buffer fails
     */
    public Set<ElementPosition> print(
        T formattable,
        Appendable buffer,
        AttributeQuery attributes
    ) throws IOException {

        ChronoDisplay display =
            this.chronology.preformat(formattable, attributes);
        return this.print(display, buffer, attributes, true);

    }

    @Override
    public <R> R print(
        T formattable,
        Appendable buffer,
        AttributeQuery attributes,
        ChronoFunction<ChronoDisplay, R> query
    ) throws IOException {

        ChronoDisplay display =
            this.chronology.preformat(formattable, attributes);
        this.print(display, buffer, attributes, false);
        return query.apply(display);

    }

    private T toEntity(TemporalAccessor formattable) {

        T entity = this.chronology.createFrom(formattable, this.globalAttributes);

        if (entity == null) {
            throw new IllegalArgumentException("Insufficient data to convert temporal accessor: " + formattable);
        }

        return entity;

    }

    private ChronoFormatter<T> toZonedFormatter(TemporalAccessor formattable) {

        if (!this.getAttributes().contains(Attributes.TIMEZONE_ID)) {
            ZoneId zoneId = formattable.query(TemporalQueries.zone());

            if (zoneId != null) {
                return this.withTimezone(zoneId.getId());
            }
        }

        return this;

    }

    private Set<ElementPosition> print(
        ChronoDisplay formattable,
        Appendable buffer,
        AttributeQuery attributes,
        boolean withPositions
    ) throws IOException {

        if (buffer == null) {
            throw new NullPointerException("Missing text result buffer.");
        }

        Set<ElementPosition> positions = null;

        if (withPositions) {
            positions = new LinkedHashSet<>(this.steps.size());
        }

        try {
            for (FormatStep step : this.steps) {
                step.print(
                    formattable,
                    buffer,
                    attributes,
                    positions
                );
            }
        } catch (ChronoException ex) {
            throw new IllegalArgumentException(
                "Not formattable: " + formattable, ex);
        }

        if (withPositions) {
            return Collections.unmodifiableSet(positions);
        } else {
            return Collections.emptySet();
        }

    }

    @Override
    public T parse(CharSequence text) throws ParseException {

        ParseLog status = new ParseLog();
        T result = this.parse(text, status, this.globalAttributes);

        if (result == null) {
            throw new ParseException(
                status.getErrorMessage(),
                status.getErrorIndex()
            );
        }

        return result;

    }

    /**
     * <p>For maximum information use {@link #parse(CharSequence, ParseLog)} instead. </p>
     *
     * @param   text        text to be parsed
     * @param   position    parse position (always as new instance)
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of text or even behind
     */
    /*[deutsch]
     * <p>F&uuml;r maximale Information stattdessen {@link #parse(CharSequence, ParseLog)} nutzen. </p>
     *
     * @param   text        text to be parsed
     * @param   position    parse position (always as new instance)
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of text or even behind
     */
    @Override
    public T parse(
        CharSequence text,
        ParsePosition position
    ) {

        return this.parse(text, new ParseLog(position), this.globalAttributes);

    }

    @Override
    public T parse(
        CharSequence text,
        ParsePosition position,
        RawValues rawValues
    ) {

        ParseLog plog = new ParseLog(position);
        T result = this.parse(text, plog, this.globalAttributes);
        rawValues.accept(plog.getRawValues());
        return result;

    }

    /**
     * <p>Interpretes given text as chronological entity starting
     * at the specified position in parse log. </p>
     *
     * <p>Equivalent to {@code parse(text, status, getAttributes())}. </p>
     *
     * @param   text        text to be parsed
     * @param   status      parser information (always as new instance)
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of
     *          text or even behind
     */
    /*[deutsch]
     * <p>Interpretiert den angegebenen Text ab der angegebenen Position im
     * Log. </p>
     *
     * <p>Entspricht {@code parse(text, status, getAttributes())}. </p>
     *
     * @param   text        text to be parsed
     * @param   status      parser information (always as new instance)
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of
     *          text or even behind
     */
    public T parse(
        CharSequence text,
        ParseLog status
    ) {

        return this.parse(text, status, this.globalAttributes);

    }

    /**
     * <p>Interpretes given text as chronological entity starting
     * at the specified position in parse log. </p>
     *
     * <p>The given attributes cannot change the inner format structure
     * (for example not change a localized weekmodel), but can override some
     * format properties like expected language or certain text attributes
     * for this run only. </p>
     *
     * @param   text        text to be parsed
     * @param   status      parser information (always as new instance)
     * @param   attributes  attributes for limited parsing control
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of
     *          text or even behind
     */
    /*[deutsch]
     * <p>Interpretiert den angegebenen Text ab der angegebenen Position
     * im Log. </p>
     *
     * <p>Die mitgegebenen Steuerattribute k&ouml;nnen nicht die innere
     * Formatstruktur &auml;ndern (zum Beispiel nicht ein lokalisiertes
     * Wochenmodell wechseln), aber bestimmte Formateigenschaften wie
     * die erwartetete Sprache oder Textattribute individuell nur f&uuml;r
     * diesen Lauf setzen. </p>
     *
     * @param   text        text to be parsed
     * @param   status      parser information (always as new instance)
     * @param   attributes  attributes for limited parsing control
     * @return  result or {@code null} if parsing does not work
     * @throws  IndexOutOfBoundsException if the start position is at end of
     *          text or even behind
     */
    @Override
    public T parse(
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes
    ) {

        T result;
        Chronology<?> preparser = this.chronology.preparser();

        if (preparser == null) {
            return parse(this, this.chronology, text, status, attributes, false);
        } else {
            Object obj = parse(this, preparser, text, status, attributes, true);

            if (
                (obj == null)
                || status.isError()
            ) {
                return null;
            }

            ParsedValues parsed = status.getRawValues0();

            try {
                result = this.chronology.createFrom(parsed, attributes, false);
            } catch (RuntimeException re) {
                status.setError(
                    text.length(),
                    re.getMessage() + getDescription(parsed.toMap()));
                return null;
            }

            if (result == null) {
                if (!status.isError()) {
                    status.setError(
                        text.length(),
                        getReason(parsed) + getDescription(parsed.toMap()));
                }
                return null;
            }

            return checkConsistency(parsed, result, text, status, attributes);
        }

    }

    /**
     * <p>Translates given text as raw chronological entity without
     * converting to the target type of the underlying chronology. </p>
     *
     * @param   text        text to be parsed
     * @return  new map-like mutable entity (empty if parsing does not work)
     * @since	2.0
     */
    /*[deutsch]
     * <p>Interpretiert den angegebenen Text zu Rohdaten, ohne eine
     * Typkonversion vorzunehmen. </p>
     *
     * @param   text        text to be parsed
     * @return  new map-like mutable entity (empty if parsing does not work)
     * @since	2.0
     */
    public ChronoEntity<?> parseRaw(String text) {

        return this.parseRaw(text, 0);

    }

    /**
     * <p>Translates given text as raw chronological entity without
     * converting to the target type of the underlying chronology. </p>
     *
     * @param   text        text to be parsed
     * @param   offset      start position
     * @return  new map-like mutable entity (empty if parsing does not work)
     * @since	2.0
     */
    /*[deutsch]
     * <p>Interpretiert den angegebenen Text zu Rohdaten, ohne eine
     * Typkonversion vorzunehmen. </p>
     *
     * @param   text        text to be parsed
     * @param   offset      start position
     * @return  new map-like mutable entity (empty if parsing does not work)
     * @since	2.0
     */
    public ChronoEntity<?> parseRaw(
        CharSequence text,
        int offset
    ) {

        if (offset >= text.length()) {
            return new ParsedValues();
        }

        ParseLog status = new ParseLog(offset);
        AttributeQuery attributes = this.globalAttributes;
        Chronology<?> c = this.chronology.preparser();

        if (c == null) {
            c = this.chronology;
        }

        // Phase 1: elementweise Interpretation und Sammeln der Elementwerte
        Deque<NonAmbivalentMap> data = new LinkedList<>();
        ParsedValues parsed = status.getRawValues0();

        try {
            parsed = this.parseElements(text, status, attributes, data);
            parsed.setNoAmbivalentCheck();
            status.setRawValues(parsed);
        } catch (AmbivalentValueException ex) {
            if (!status.isError()) {
                status.setError(status.getPosition(), ex.getMessage());
            }
        }

        if (status.isError()) {
            return new ParsedValues();
        }

        assert (parsed != null);

        // Phase 2: Anreicherung mit Default-Werten
        for (ChronoElement<?> e : this.defaults.keySet()) {
            if (!parsed.contains(e)) {
                fill(parsed, e, this.defaults.get(e));
            }
        }

        // Phase 3: Auflösung von Elementwerten in chronologischen Erweiterungen
        for (ChronoExtension ext : c.getExtensions()) {
            parsed = ext.resolve(parsed, this.getLocale(), attributes);
        }

        return parsed;

    }

    /**
     * <p>Creates a copy of this formatter with given locale. </p>
     *
     * <p>Note: Sectional attributes will never be overridden. Is the
     * locale not changed then the method will simply return this instance.
     * Otherwise the copy will contain given locale and also adjusts
     * the associated numerical symbols: </p>
     *
     * <ul>
     *  <li>{@link Attributes#LANGUAGE}</li>
     *  <li>{@link Attributes#ZERO_DIGIT}</li>
     *  <li>{@link Attributes#DECIMAL_SEPARATOR}</li>
     * </ul>
     *
     * <p>If necessary all inner format elements which are locale-dependent
     * will also be adjusted. Some country-specific extensions like
     * {@link net.time4j.Weekmodel#weekOfYear()} or {@link ChronoHistory#era()}
     * will only be adjusted if the country-part of given locale is not empty.
     * However, fixed literals will remain unchanged hence this method might
     * not be suitable for all languages. Example: </p>
     *
     * <p>{@code new Locale("sv")} will only change the language to swedish
     * with no side effects, but {@code new Locale("sv", "SE")} will also
     * set the week model and the historical swedish calendar which can be
     * relevant for the years 1700-1712. </p>
     *
     * @param   locale      new language and country configuration
     * @return  changed copy with given language and localized symbols while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit der alternativ angegebenen
     * Sprach- und L&auml;ndereinstellung. </p>
     *
     * <p>Hinweise: Sektionale Attribute werden grunds&auml;tzlich nicht
     * &uuml;bersteuert. Ist die Einstellung gleich, wird keine Kopie, sondern
     * diese Instanz zur&uuml;ckgegeben, andernfalls werden neben der Sprache
     * automatisch die numerischen Symbole mit angepasst: </p>
     *
     * <ul>
     *  <li>{@link Attributes#LANGUAGE}</li>
     *  <li>{@link Attributes#ZERO_DIGIT}</li>
     *  <li>{@link Attributes#DECIMAL_SEPARATOR}</li>
     * </ul>
     *
     * <p>Angepasst werden bei Bedarf auch innere Formatelemente, die
     * Bestandteil landesabh&auml;ngiger chronologischer Erweiterungen wie
     * zum Beispiel {@link net.time4j.Weekmodel#weekOfYear()} oder
     * {@link ChronoHistory#era()} sind, aber nur dann, wenn die landesspezifische
     * Komponente des angegebenen Arguments nicht leer ist. Jedoch bleiben
     * feste Literale unver&auml;ndert, so da&szlig; diese Methode nicht f&uuml;r
     * alle Sprachen geeignet sein mag. Beispiel: </p>
     *
     * <p>{@code new Locale("sv")} wird nur die Sprache zu Schwedisch ab&auml;ndern,
     * w&auml;hrend {@code new Locale("sv", "SE")} auch das Wochenmodell und den
     * schwedischen Kalender setzt, welcher f&uuml;r die Jahre 1700-1712 relevant
     * sein kann. </p>
     *
     * @param   locale      new language and country configuration
     * @return  changed copy with given language and localized symbols while
     *          this instance remains unaffected
     */
    @Override
    public ChronoFormatter<T> with(Locale locale) {

        if (locale.equals(this.globalAttributes.getLocale())) {
            return this;
        }

        return new ChronoFormatter<>(this, this.globalAttributes.withLocale(locale));

    }

    @Override
    public ChronoFormatter<T> with(Leniency leniency) {

        return this.with(Attributes.LENIENCY, leniency);

    }

    /**
     * <p>Creates a copy of this formatter with alternative era names. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @return  changed copy with alternative era names while this instance remains unaffected
     * @see     ChronoHistory#ATTRIBUTE_COMMON_ERA
     * @see     net.time4j.history.HistoricEra#getAlternativeName(Locale, TextWidth)
     * @since   3.0
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie, die statt der Standard-&Auml;ra-Bezeichnungen
     * alternative Namen verwendet. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @return  changed copy with alternative era names while this instance remains unaffected
     * @see     ChronoHistory#ATTRIBUTE_COMMON_ERA
     * @see     net.time4j.history.HistoricEra#getAlternativeName(Locale, TextWidth)
     * @since   3.0
     */
    public ChronoFormatter<T> withAlternativeEraNames() {

        return this.with(
            ChronoHistory.ATTRIBUTE_COMMON_ERA, Boolean.TRUE
        ).with(
            ChronoHistory.ATTRIBUTE_LATIN_ERA, Boolean.FALSE
        );

    }

    /**
     * <p>Creates a copy of this formatter with latin era names. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @return  changed copy with latin era names while this instance remains unaffected
     * @see     ChronoHistory#ATTRIBUTE_LATIN_ERA
     * @since   3.1
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie, die statt der Standard-&Auml;ra-Bezeichnungen
     * lateinische Namen verwendet. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @return  changed copy with latin era names while this instance remains unaffected
     * @see     ChronoHistory#ATTRIBUTE_LATIN_ERA
     * @since   3.1
     */
    public ChronoFormatter<T> withLatinEraNames() {

        return this.with(
            ChronoHistory.ATTRIBUTE_COMMON_ERA, Boolean.FALSE
        ).with(
            ChronoHistory.ATTRIBUTE_LATIN_ERA, Boolean.TRUE
        );

    }

    /**
     * <p>Short-cut for {@code with(ChronoHistory.ofGregorianReform(date))}. </p>
     *
     * @param   date        first gregorian date after gregorian calendar reform takes effect
     * @return  changed copy with given date of gregorian calendar reform while this instance remains unaffected
     * @throws  IllegalArgumentException if given date is before first introduction of gregorian calendar
     *          on 1582-10-15 and not the minimum on the date axis
     * @see     ChronoHistory#ofGregorianReform(PlainDate)
     * @see     #with(ChronoHistory)
     * @since   3.0
     */
    /*[deutsch]
     * <p>Abk&uuml;rzung f&uuml;r {@code with(ChronoHistory.ofGregorianReform(date))}. </p>
     *
     * @param   date        first gregorian date after gregorian calendar reform takes effect
     * @return  changed copy with given date of gregorian calendar reform while this instance remains unaffected
     * @throws  IllegalArgumentException if given date is before first introduction of gregorian calendar
     *          on 1582-10-15 and not the minimum on the date axis
     * @see     ChronoHistory#ofGregorianReform(PlainDate)
     * @see     #with(ChronoHistory)
     * @since   3.0
     */
    public ChronoFormatter<T> withGregorianCutOver(PlainDate date) {

        return this.with(ChronoHistory.ofGregorianReform(date));

    }

    /**
     * <p>Creates a copy of this formatter with the given chronological history of gregorian calendar reforms. </p>
     *
     * <p>Note that this configuration will override any gregorian cutover date which might be inferred
     * from current locale. </p>
     *
     * @param   history             chronological history describing historical calendar reforms
     * @return  changed copy with given history while this instance remains unaffected
     * @since   3.1
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie, die die angegebene Kalenderreform verwendet. </p>
     *
     * <p>Zu beachten: Diese Methode wird jedes gregorianische Umstellungsdatum &uuml;berschreiben, das
     * von der L&auml;ndereinstellung dieses Formatierers abgeleitet werden mag. </p>
     *
     * @param   history             chronological history describing historical calendar reforms
     * @return  changed copy with given history while this instance remains unaffected
     * @since   3.1
     */
    public ChronoFormatter<T> with(ChronoHistory history) {

        Attributes attrs =
            new Attributes.Builder()
                .setAll(this.globalAttributes.getAttributes())
                .set(ChronoHistory.ATTRIBUTE_HISTORIC_VARIANT, history.getVariant())
                .build();
        PlainDate cutover = null;

        if (history.getVariant() == HistoricVariant.SINGLE_CUTOVER_DATE) {
            cutover = history.getGregorianCutOverDate();
        }

        AttributeSet as =
            new AttributeSet(
                attrs,
                this.globalAttributes.getLocale(),
                this.globalAttributes.getLevel(),
                this.globalAttributes.getSection(),
                this.globalAttributes.getCondition(),
                cutover);

        return new ChronoFormatter<>(this, as, history);

    }

    /**
     * <p>Creates a copy of this formatter with given timezone id which
     * shall be used in formatting or parsing. </p>
     *
     * <p>The timezone is in most cases only relevant for the type
     * {@link net.time4j.Moment}. When formatting the timezone helps
     * to convert the UTC value into a zonal representation. When
     * parsing the timezone serves as replacement value if the formatted
     * text does not contain any timezone. </p>
     *
     * @param   tzid        timezone id
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     * @see     Attributes#TIMEZONE_ID
     *
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit der angegebenen Zeitzone, die beim
     * Formatieren oder Parsen verwendet werden soll. </p>
     *
     * <p>Die Zeitzone ist nur f&uuml;r den Typ {@link net.time4j.Moment}
     * von Bedeutung. Beim Formatieren wandelt sie die UTC-Darstellung in
     * eine zonale Repr&auml;sentation um. Beim Parsen dient sie als
     * Ersatzwert, wenn im zu interpretierenden Text keine Zeitzone
     * gefunden werden konnte. </p>
     *
     * @param   tzid        timezone id
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     * @see     Attributes#TIMEZONE_ID
     */
    @Override
    public ChronoFormatter<T> withTimezone(TZID tzid) {

        if (tzid == null) {
            throw new NullPointerException("Missing timezone id.");
        }

        Attributes attrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .setTimezone(tzid)
            .build();
        return new ChronoFormatter<>(this, attrs);

    }

    /**
     * <p>Equivalent to {@link #withTimezone(TZID)
     * withTimezone(Timezone.of(tzid).getID())}. </p>
     *
     * @param   tzid    timezone id
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     * @throws  IllegalArgumentException if given timezone cannot be loaded
     * @see     #withTimezone(TZID)
     * @see     Attributes#TIMEZONE_ID
     * @since   1.1
     */
    /*[deutsch]
     * <p>Entspricht {@link #withTimezone(TZID)
     * withTimezone(Timezone.of(tzid).getID())}. </p>
     *
     * @param   tzid    timezone id
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     * @throws  IllegalArgumentException if given timezone cannot be loaded
     * @see     #withTimezone(TZID)
     * @see     Attributes#TIMEZONE_ID
     * @since   1.1
     */
    @Override
    public ChronoFormatter<T> withTimezone(String tzid) {

        return this.withTimezone(Timezone.of(tzid).getID());

    }

    /**
     * <p>Equivalent to {@link #withTimezone(TZID)
     * withTimezone(Timezone.ofSystem().getID())}. </p>
     *
     * @return  changed copy with the system timezone while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Entspricht {@link #withTimezone(TZID)
     * withTimezone(Timezone.ofSystem().getID())}. </p>
     *
     * @return  changed copy with the system timezone while
     *          this instance remains unaffected
     */
    public ChronoFormatter<T> withStdTimezone() {

        return this.withTimezone(Timezone.ofSystem().getID());

    }

    /**
     * <p>Determines a default replacement value for given element. </p>
     *
     * <p>Example: </p>
     *
     * <pre>
     *  ChronoFormatter&lt;PlainDate&gt; fmt =
     *      PlainDate.localFormatter("MM-dd", PatternType.CLDR)
     *               .withDefault(PlainDate.YEAR, 2012);
     *  PlainDate date = fmt.parse("05-21");
     *  System.out.println(date); // 2012-05-21
     * </pre>
     *
     * <p>Default replacement values will be considered by Time4J if either
     * the formatter does not contain the element in question at all or if
     * there are no consumable characters for given element. Latter
     * situation might sometimes require the use of sectional attribute
     * {@code PROTECTED_CHARACTERS} in order to simulate an end-of-text
     * situation. </p>
     *
     * @param   <V> generic element value type
     * @param   element     chronological element to be updated
     * @param   value       replacement value or {@code null}
     *                      if the default value shall be deregistered
     * @return  changed copy with new replacement value
     * @throws  IllegalArgumentException if given element is not supported
     *          by the underlying chronology
     * @see     Attributes#PROTECTED_CHARACTERS
     */
    /*[deutsch]
     * <p>Legt einen Standard-Ersatzwert f&uuml;r das angegebene Element
     * fest, wenn die Interpretation sonst nicht funktioniert. </p>
     *
     * <p>Beispiel: </p>
     *
     * <pre>
     *  ChronoFormatter&lt;PlainDate&gt; fmt =
     *      PlainDate.localFormatter("MM-dd", PatternType.CLDR)
     *               .withDefault(PlainDate.YEAR, 2012);
     *  PlainDate date = fmt.parse("05-21");
     *  System.out.println(date); // 2012-05-21
     * </pre>
     *
     * <p>Standard-Ersatzwerte werden von Time4J herangezogen, wenn entweder
     * der Formatierer das fragliche Element nicht enth&auml;lt oder wenn es
     * keine konsumierbaren Zeichen f&uuml;r das angegebene Element gibt.
     * Die letzte Situation erfordert manchmal die Verwendung des sektionalen
     * Attributs {@code PROTECTED_CHARACTERS}, um eine Situation zu simulieren,
     * in der der Formatierer quasi am Ende eines Texts angekommen ist. </p>
     *
     * @param   <V> generic element value type
     * @param   element     chronological element to be updated
     * @param   value       replacement value or {@code null}
     *                      if the default value shall be deregistered
     * @return  changed copy with new replacement value
     * @throws  IllegalArgumentException if given element is not supported
     *          by the underlying chronology
     * @see     Attributes#PROTECTED_CHARACTERS
     */
    public <V> ChronoFormatter<T> withDefault(
        ChronoElement<V> element,
        V value
    ) {

        return new ChronoFormatter<>(this, element, value);

    }

    /**
     * <p>Creates a copy of this formatter with given boolean-attribute. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit dem angegebenen boolean-Attribut. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    public ChronoFormatter<T> with(
        AttributeKey<Boolean> key,
        boolean value
    ) {

        Attributes attrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .set(key, value)
            .build();
        return new ChronoFormatter<>(this, attrs);

    }

    /**
     * <p>Creates a copy of this formatter with given int-attribute. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit dem angegebenen int-Attribut. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    public ChronoFormatter<T> with(
        AttributeKey<Integer> key,
        int value
    ) {

        Attributes attrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .set(key, value)
            .build();
        return new ChronoFormatter<>(this, attrs);

    }

    /**
     * <p>Creates a copy of this formatter with given char-attribute. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit dem angegebenen char-Attribut. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    public ChronoFormatter<T> with(
        AttributeKey<Character> key,
        char value
    ) {

        Attributes attrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .set(key, value)
            .build();
        return new ChronoFormatter<>(this, attrs);

    }

    /**
     * <p>Creates a copy of this formatter with given enum-attribute. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @param   <A> generic attribute value type
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit dem angegebenen enum-Attribut. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @param   <A> generic attribute value type
     * @param   key     attribute key
     * @param   value   attribute value
     * @return  changed copy with the new or changed attribute while
     *          this instance remains unaffected
     */
    public <A extends Enum<A>> ChronoFormatter<T> with(
        AttributeKey<A> key,
        A value
    ) {

        Attributes attrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .set(key, value)
            .build();
        return new ChronoFormatter<>(this, attrs);

    }

    /**
     * <p>Creates a copy of this formatter with given standard attributes. </p>
     *
     * <p>Note: Sectional attributes cannot be overridden. </p>
     *
     * @param   attributes  new default attributes
     * @return  changed copy with the new or changed attributes while
     *          this instance remains unaffected
     */
    /*[deutsch]
     * <p>Erzeugt eine Kopie mit den angegebenen Standard-Attributen. </p>
     *
     * <p>Hinweis: Sektionale Attribute werden nicht &uuml;bersteuert. </p>
     *
     * @param   attributes  new default attributes
     * @return  changed copy with the new or changed attributes while
     *          this instance remains unaffected
     */
    public ChronoFormatter<T> with(Attributes attributes) {

        Attributes newAttrs =
            new Attributes.Builder()
            .setAll(this.globalAttributes.getAttributes())
            .setAll(attributes)
            .build();
        return new ChronoFormatter<>(this, newAttrs);

    }

    /**
     * <p>Converts this formatter into a traditional
     * {@code java.text.Format}-object. </p>
     *
     * <p>The returned format object also supports attributed strings such
     * that all {@code ChronoElement}-structures are associated with field
     * attributes of type {@link java.text.DateFormat.Field DateFormat.Field}.
     * In ISO systems following mapping will be applied: </p>
     *
     * <ul>
     *  <li>{@link net.time4j.PlainTime#AM_PM_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#AM_PM}</li>
     *  <li>{@link net.time4j.PlainTime#CLOCK_HOUR_OF_AMPM} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR1}</li>
     *  <li>{@link net.time4j.PlainTime#CLOCK_HOUR_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR_OF_DAY1}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_MONTH} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_WEEK} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_YEAR}</li>
     *  <li>{@link net.time4j.PlainTime#DIGITAL_HOUR_OF_AMPM} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR1}</li>
     *  <li>{@link net.time4j.PlainTime#DIGITAL_HOUR_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR0}</li>
     *  <li>{@link net.time4j.PlainTime#MILLI_OF_SECOND} =&gt;
     *  {@link java.text.DateFormat.Field#MILLISECOND}</li>
     *  <li>{@link net.time4j.PlainTime#MINUTE_OF_HOUR} =&gt;
     *  {@link java.text.DateFormat.Field#MINUTE}</li>
     *  <li>{@link net.time4j.PlainDate#MONTH_AS_NUMBER} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#MONTH_OF_YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link net.time4j.PlainTime#SECOND_OF_MINUTE} =&gt;
     *  {@link java.text.DateFormat.Field#SECOND}</li>
     *  <li>{@link net.time4j.PlainDate#WEEKDAY_IN_MONTH} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK_IN_MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link net.time4j.PlainDate#YEAR_OF_WEEKDATE} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#boundedWeekOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_MONTH}</li>
     *  <li>{@link net.time4j.Weekmodel#boundedWeekOfYear()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#weekOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_MONTH}</li>
     *  <li>{@link net.time4j.Weekmodel#weekOfYear()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#localDayOfWeek()} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK}</li>
     *  <li>{@link net.time4j.engine.ChronoEntity#getTimezone()} =&gt;
     *  {@link java.text.DateFormat.Field#TIME_ZONE}</li>
     *  <li>{@link ChronoHistory#era()} =&gt;
     *  {@link java.text.DateFormat.Field#ERA}</li>
     *  <li>{@link ChronoHistory#yearOfEra()} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link ChronoHistory#month()} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link ChronoHistory#dayOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_MONTH}</li>
     * </ul>
     *
     * <p>Note: The returned {@code Format}-object is not serializable. </p>
     *
     * @return  new non-serializable {@code java.text.Format}-object which
     *          delegates all formatting and parsing work to this instance
     */
    /*[deutsch]
     * <p>Wandelt dieses Objekt in ein herk&ouml;mmliches
     * {@code java.text.Format}-Objekt um. </p>
     *
     * <p>Das erzeugte Format-Objekt unterst&uuml;tzt auch attributierte
     * Strings, indem versucht wird, allen {@code ChronoElement}-Strukturen
     * Attribute vom Typ {@link java.text.DateFormat.Field DateFormat.Field}
     * zuzuweisen. In ISO-Systemen wird folgende Abbildung verwendet: </p>
     *
     * <ul>
     *  <li>{@link net.time4j.PlainTime#AM_PM_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#AM_PM}</li>
     *  <li>{@link net.time4j.PlainTime#CLOCK_HOUR_OF_AMPM} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR1}</li>
     *  <li>{@link net.time4j.PlainTime#CLOCK_HOUR_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR_OF_DAY1}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_MONTH} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_WEEK} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK}</li>
     *  <li>{@link net.time4j.PlainDate#DAY_OF_YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_YEAR}</li>
     *  <li>{@link net.time4j.PlainTime#DIGITAL_HOUR_OF_AMPM} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR1}</li>
     *  <li>{@link net.time4j.PlainTime#DIGITAL_HOUR_OF_DAY} =&gt;
     *  {@link java.text.DateFormat.Field#HOUR0}</li>
     *  <li>{@link net.time4j.PlainTime#MILLI_OF_SECOND} =&gt;
     *  {@link java.text.DateFormat.Field#MILLISECOND}</li>
     *  <li>{@link net.time4j.PlainTime#MINUTE_OF_HOUR} =&gt;
     *  {@link java.text.DateFormat.Field#MINUTE}</li>
     *  <li>{@link net.time4j.PlainDate#MONTH_AS_NUMBER} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#MONTH_OF_YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link net.time4j.PlainTime#SECOND_OF_MINUTE} =&gt;
     *  {@link java.text.DateFormat.Field#SECOND}</li>
     *  <li>{@link net.time4j.PlainDate#WEEKDAY_IN_MONTH} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK_IN_MONTH}</li>
     *  <li>{@link net.time4j.PlainDate#YEAR} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link net.time4j.PlainDate#YEAR_OF_WEEKDATE} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#boundedWeekOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_MONTH}</li>
     *  <li>{@link net.time4j.Weekmodel#boundedWeekOfYear()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#weekOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_MONTH}</li>
     *  <li>{@link net.time4j.Weekmodel#weekOfYear()} =&gt;
     *  {@link java.text.DateFormat.Field#WEEK_OF_YEAR}</li>
     *  <li>{@link net.time4j.Weekmodel#localDayOfWeek()} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_WEEK}</li>
     *  <li>{@link net.time4j.engine.ChronoEntity#getTimezone()} =&gt;
     *  {@link java.text.DateFormat.Field#TIME_ZONE}</li>
     *  <li>{@link ChronoHistory#era()} =&gt;
     *  {@link java.text.DateFormat.Field#ERA}</li>
     *  <li>{@link ChronoHistory#yearOfEra()} =&gt;
     *  {@link java.text.DateFormat.Field#YEAR}</li>
     *  <li>{@link ChronoHistory#month()} =&gt;
     *  {@link java.text.DateFormat.Field#MONTH}</li>
     *  <li>{@link ChronoHistory#dayOfMonth()} =&gt;
     *  {@link java.text.DateFormat.Field#DAY_OF_MONTH}</li>
     * </ul>
     *
     * <p>Zu beachten: Das {@code Format}-Objekt ist nicht serialisierbar. </p>
     *
     * @return  new non-serializable {@code java.text.Format}-object which
     *          delegates all formatting and parsing work to this instance
     */
    public Format toFormat() {

        return new TraditionalFormat<>(this);

    }

    /**
     * <p>Constructs a pattern-based formatter for plain date objects. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    /*[deutsch]
     * <p>Konstruiert einen Formatierer f&uuml;r reine Datumsobjekte. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    public static ChronoFormatter<PlainDate> ofDatePattern(
        String pattern,
        PatternType type,
        Locale locale
    ) {

        Builder<PlainDate> builder = new Builder<>(PlainDate.class, locale);
        builder.addPattern(pattern, type);
        return builder.build();

    }

    /**
     * <p>Constructs a pattern-based formatter for clock time objects. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    /*[deutsch]
     * <p>Konstruiert einen Formatierer f&uuml;r Uhrzeitobjekte. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    public static ChronoFormatter<PlainTime> ofTimePattern(
        String pattern,
        PatternType type,
        Locale locale
    ) {

        Builder<PlainTime> builder = new Builder<>(PlainTime.class, locale);
        builder.addPattern(pattern, type);
        return builder.build();

    }

    /**
     * <p>Constructs a pattern-based formatter for plain timestamps. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    /*[deutsch]
     * <p>Konstruiert einen Formatierer f&uuml;r einfache Zeitstempelobjekte. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @return  new {@code ChronoFormatter}-instance
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    public static ChronoFormatter<PlainTimestamp> ofTimestampPattern(
        String pattern,
        PatternType type,
        Locale locale
    ) {

        Builder<PlainTimestamp> builder = new Builder<>(PlainTimestamp.class, locale);
        builder.addPattern(pattern, type);
        return builder.build();

    }

    /**
     * <p>Constructs a pattern-based formatter for global timestamp objects. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @param   tzid            timezone id
     * @return  new format object for formatting {@code Moment}-objects using given locale and timezone
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    /*[deutsch]
     * <p>Konstruiert einen Formatierer f&uuml;r globale Zeitstempelobjekte. </p>
     *
     * @param   pattern     format pattern
     * @param   type        the type of the pattern to be used
     * @param   locale      format locale
     * @param   tzid            timezone id
     * @return  new format object for formatting {@code Moment}-objects using given locale and timezone
     * @throws  IllegalArgumentException if resolving of pattern fails
     * @since   3.1
     */
    public static ChronoFormatter<Moment> ofMomentPattern(
        String pattern,
        PatternType type,
        Locale locale,
        TZID tzid
    ) {

        Builder<Moment> builder = new Builder<>(Moment.class, locale);
        builder.addPattern(pattern, type);
        return builder.build().withTimezone(tzid);

    }

    /**
     * <p>Constructs a builder for creating formatters. </p>
     *
     * @param   <T> generic chronological type (subtype of {@code ChronoEntity})
     * @param   type        reified chronological type
     * @param   locale      format locale
     * @return  new {@code Builder}-instance
     * @throws  IllegalArgumentException if given chronological type is not
     *          formattable that is if no chronology can be derived from type
     * @see     Chronology#lookup(Class)
     */
    /*[deutsch]
     * <p>Konstruiert ein Hilfsobjekt zum Bauen eines Zeitformats. </p>
     *
     * @param   <T> generic chronological type (subtype of {@code ChronoEntity})
     * @param   type        reified chronological type
     * @param   locale      format locale
     * @return  new {@code Builder}-instance
     * @throws  IllegalArgumentException if given chronological type is not
     *          formattable that is if no chronology can be derived from type
     * @see     Chronology#lookup(Class)
     */
    public static <T extends ChronoEntity<T>> ChronoFormatter.Builder<T> setUp(
        Class<T> type,
        Locale locale
    ) {

        return new Builder<>(type, locale);

    }

    /**
     * <p>Compares the chronologies, default attributes, default values and
     * the internal format structures. </p>
     */
    /*[deutsch]
     * <p>Vergleicht die Chronologien, Standard-Attribute, Standard-Ersatzwerte
     * und die internen Formatstrukturen. </p>
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        } else if (obj instanceof ChronoFormatter) {
            ChronoFormatter<?> that = (ChronoFormatter<?>) obj;
            return (
                this.chronology.equals(that.chronology)
                && this.globalAttributes.equals(that.globalAttributes)
                && this.defaults.equals(that.defaults)
                && this.steps.equals(that.steps)
            );
        } else {
            return false;
        }

    }

    /*[deutsch]
     * <p>Berechnet den Hash-Code basierend auf dem internen Zustand. </p>
     */
    @Override
    public int hashCode() {

        return (
            7 * this.chronology.hashCode()
            + 31 * this.globalAttributes.hashCode()
            + 37 * this.steps.hashCode());

    }

    /**
     * <p>For debugging purposes. </p>
     */
    /*[deutsch]
     * <p>F&uuml;r Debugging-Zwecke. </p>
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(256);
        sb.append("net.time4j.format.ChronoFormatter[chronology=");
        sb.append(this.chronology.getChronoType().getName());
        sb.append(", default-attributes=");
        sb.append(this.globalAttributes);
        sb.append(", default-values=");
        sb.append(this.defaults);
        sb.append(", processors=");
        boolean first = true;
        for (FormatStep d : this.steps) {
            if (first) {
                first = false;
                sb.append('{');
            } else {
                sb.append('|');
            }
            sb.append(d);
        }
        sb.append("}]");
        return sb.toString();

    }

    private static <T extends ChronoEntity<T>> T parse(
        ChronoFormatter<?> cf,
        Chronology<T> chronology,
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes,
        boolean preparsing
    ) {

        if (status.getPosition() >= text.length()) {
            throw new IndexOutOfBoundsException(
                "[" + status.getPosition() + "]: " + text.toString());
        }

        // Phase 1: elementweise Interpretation und Sammeln der Elementwerte
        Deque<NonAmbivalentMap> data = new LinkedList<>();
        ParsedValues parsed = status.getRawValues0();

        try {
            parsed = cf.parseElements(text, status, attributes, data);
            parsed.setNoAmbivalentCheck();
            status.setRawValues(parsed);
        } catch (AmbivalentValueException ex) {
            if (!status.isError()) {
                status.setError(status.getPosition(), ex.getMessage());
            }
        }

        if (status.isError()) {
            return null;
        }

        int index = status.getPosition();
        assert (parsed != null);

        if (
            (index < text.length())
            && !attributes.get(
                Attributes.TRAILING_CHARACTERS,
                Boolean.FALSE).booleanValue()
        ) {
            status.setError(
                index,
                "Unparsed trailing characters: " + sub(index, text));
            return null;
        }

        // Phase 2: Anreicherung mit Default-Werten
        for (ChronoElement<?> e : cf.defaults.keySet()) {
            if (!parsed.contains(e)) {
                fill(parsed, e, cf.defaults.get(e));
            }
        }

        // Phase 3: Auflösung von Elementwerten in chronologischen Erweiterungen
        for (ChronoExtension ext : chronology.getExtensions()) {
            parsed = ext.resolve(parsed, cf.getLocale(), attributes);
        }

        // Phase 4: Transformation der Elementwerte zum Typ T (ChronoMerger)
        T result;

        try {
            result = chronology.createFrom(parsed, attributes, preparsing);
        } catch (RuntimeException re) {
            status.setError(
                text.length(),
                re.getMessage() + getDescription(data.peek()));
            return null;
        }

        if (
            (cf.fracproc != null)
            && (result != null)
        ) { // Sonderfall Bruchzahlelement
            result = cf.fracproc.update(result, parsed);
        }

        if (
            preparsing
            && (result != null)
            && (chronology instanceof TimeAxis)
        ) {
            ChronoElement<?> self = TimeAxis.class.cast(chronology).element();
            updateSelf(parsed, self, result);
        }

        // Phase 5: Konsistenzprüfung
        if (result == null) {
            status.setError(
                text.length(),
                getReason(parsed) + getDescription(data.peek()));
            return null;
        } else {
            return checkConsistency(parsed, result, text, status, attributes);
        }

    }

    private static String getReason(ParsedValues parsed) {

        String reason;

        if (parsed.contains(ValidationElement.ERROR_MESSAGE)) {
            reason =
                "Validation failed => "
                + parsed.get(ValidationElement.ERROR_MESSAGE);
            parsed.with(ValidationElement.ERROR_MESSAGE, null);
        } else {
            reason = "Insufficient data:";
        }

        return reason;

    }

    private static <T> void updateSelf(
        ParsedValues parsed,
        ChronoElement<T> element,
        Object result
    ) {

        parsed.with(element, element.getType().cast(result));

    }

    private static <T extends ChronoEntity<T>> T checkConsistency(
        ParsedValues parsed,
        T result,
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes
    ) {

        Leniency leniency =
            attributes.get(Attributes.LENIENCY, Leniency.SMART);

        if (leniency.isStrict()) {
            // Zeitzonenkonversion ergibt immer Unterschied zwischen
            // lokaler und globaler Zeit => nicht prüfen!
            if (result instanceof UnixTime) {
                if (status.getDSTInfo() != null) {
                    TZID tzid = parsed.getTimezone();
                    UnixTime ut = UnixTime.class.cast(result);

                    try {
                        boolean dst = Timezone.of(tzid).isDaylightSaving(ut);
                        if (dst != status.getDSTInfo().booleanValue()) {
                            StringBuilder reason = new StringBuilder(256);
                            reason.append("Conflict found: ");
                            reason.append("Parsed entity is ");
                            if (!dst) {
                                reason.append("not ");
                            }
                            reason.append("daylight-saving, but timezone name");
                            reason.append(" has not the appropriate form in {");
                            reason.append(text.toString());
                            reason.append("}.");
                            status.setError(text.length(), reason.toString());
                            result = null;
                        }
                    } catch (IllegalArgumentException iae) {
                        StringBuilder reason = new StringBuilder(256);
                        reason.append("Unable to check timezone name: ");
                        reason.append(iae.getMessage());
                        status.setError(text.length(), reason.toString());
                        return null;
                    }
                }
            } else {
                for (ChronoElement<?> e : parsed.toMap().keySet()) {
                    Object value = parsed.get(e);

                    if (
                        result.contains(e)
                        && !result.get(e).equals(value)
                    ) {
                        StringBuilder reason = new StringBuilder(256);
                        reason.append("Conflict found: ");
                        reason.append("Text {");
                        reason.append(text.toString());
                        reason.append("} with element ");
                        reason.append(e.name());
                        reason.append(" {");
                        reason.append(value);
                        reason.append("}, but parsed entity ");
                        reason.append("has element value {");
                        reason.append(result.get(e));
                        reason.append("}.");
                        status.setError(text.length(), reason.toString());
                        return null;
                    }
                }
            }
        }

        return result;

    }

    private ParsedValues parseElements(
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes,
        Deque<NonAmbivalentMap> data
    ) {

        NonAmbivalentMap values = new NonAmbivalentMap();
        values.put(null, Integer.valueOf(status.getPosition()));
        data.push(values);
        int previous = 0;
        int current = 0;
        int index = 0;
        int len = this.steps.size();

        while (index < len) {
            FormatStep step = this.steps.get(index);
            current = step.getLevel();
            int level = current;

            // Start einer optionalen Sektion: Stack erweitern
            while (level > previous) {
                values = new NonAmbivalentMap();
                values.put(null, Integer.valueOf(status.getPosition()));
                data.push(values);
                level--;
            }

            // Ende einer optionalen Sektion: Werte im Stack sichern
            while (level < previous) {
                values = data.pop();
                values.remove(null);
                data.peek().putAll(values);
                level++;
            }

            // Delegation der Element-Verarbeitung
            Map<ChronoElement<?>, Object> parsedResult = data.peek();
            status.clearWarning();
            step.parse(text, status, attributes, parsedResult);

            // Im Warnzustand default-value verwenden?
            if (status.isWarning()) {
                ChronoElement<?> element = step.getProcessor().getElement();
                if (
                    (element != null)
                    && this.defaults.containsKey(element)
                ) {
                    parsedResult.put(element, this.defaults.get(element));
                    status.clearError();
                    status.clearWarning();
                }
            }

            // Fehler-Auflösung
            if (status.isError()) {
                if (current == 0) {
                    // Grundzustand => aussteigen
                    values = data.peek();
                    values.remove(null);
                    return new ParsedValues(values);
                } else {
                    // Ende des optionalen Abschnitts suchen
                    int section = step.getSection();
                    int last = index;
                    for (int j = len - 1; j > index; j--) {
                        if (this.steps.get(j).getSection() == section) {
                            last = j;
                            break;
                        }
                    }
                    index = last;
                    // Restauration der alten Werte und der Fehlerinformation
                    current--;
                    values = data.pop();
                    status.clearError();
                    status.setPosition(((Integer) values.get(null)).intValue());
                }
            }

            // Schleifenzähler inkrementieren
            previous = current;
            index++;
        }

        // Verbleibende optionale Sektionen auflösen
        while (current > 0) {
            values = data.pop();
            values.remove(null);
            data.peek().putAll(values);
            current--;
        }

        // Ergebnis
        values = data.peek();
        values.remove(null);
        return new ParsedValues(values);

    }

    private static String sub(
        int index,
        CharSequence text
    ) {

        int len = text.length();

        if (len - index <= 10) {
            return text.subSequence(index, len).toString();
        }

        return text.subSequence(index, index + 10).toString() + "...";

    }

    private static String getDescription(Map<ChronoElement<?>, Object> map) {

        StringBuilder sb = new StringBuilder(map.size() * 16);
        sb.append(" [parsed={");
        boolean first = true;

        for (ChronoElement<?> element : map.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }

            sb.append(element.name());
            sb.append('=');
            sb.append(map.get(element));
        }

        sb.append("}]");
        return sb.toString();

    }

    // wildcard capture
    private static <V> void fill(
        ChronoEntity<?> entity,
        ChronoElement<V> element,
        Object value
    ) {

        entity.with(element, element.getType().cast(value));

    }

    private static void checkElement(
        Chronology<?> chronology,
        ChronoElement<?> element
    ) {

        if (!chronology.isSupported(element)) {
            Chronology<?> child = chronology.preparser();

            if (
                (child == null)
                || !child.isSupported(element)
            ) {
                throw new IllegalArgumentException(
                    "Unsupported element: " + element.name());
            }
        }

    }

    //~ Innere Klassen ----------------------------------------------------

    /**
     * <p>Builder for creating a new {@code ChronoFormatter}. </p>
     *
     * <p>This class is not <i>thread-safe</i> so a new instance is
     * necessary per thread. A new instance can be created by
     * {@link ChronoFormatter#setUp(Class, Locale)}. </p>
     *
     * @param       <T> generic type of chronological entity
     *              (subtype of {@code ChronoEntity})
     * @author      Meno Hochschild
     * @doctags.concurrency {mutable}
     */
    /*[deutsch]
     * <p>Erzeugt ein neues Formatobjekt. </p>
     *
     * <p>Je Thread wird eine neue {@code Builder}-Instanz ben&ouml;tigt,
     * weil diese Klasse nicht <i>thread-safe</i> ist. Eine neue Instanz
     * wird mittels {@link ChronoFormatter#setUp(Class, Locale)} erzeugt. </p>
     *
     * @param       <T> generic type of chronological entity
     *              (subtype of {@code ChronoEntity})
     * @author      Meno Hochschild
     * @doctags.concurrency {mutable}
     */
    public static final class Builder<T extends ChronoEntity<T>> {

        //~ Instanzvariablen ----------------------------------------------

        private final Chronology<T> chronology;
        private final Locale locale;
        private List<FormatStep> steps;
        private LinkedList<AttributeSet> stack;
        private int sectionID;
        private int reservedIndex;
        private int leftPadWidth;
        private boolean prolepticGregorian;

        //~ Konstruktoren -------------------------------------------------

        private Builder(
            Class<T> chronoType,
            Locale locale
        ) {
            super();

            if (chronoType == null) {
                throw new NullPointerException("Missing chronological type.");
            } else if (locale == null) {
                throw new NullPointerException("Missing locale.");
            }

            this.chronology = Chronology.lookup(chronoType);
            this.locale = locale;

            if (this.chronology == null) {
                throw new IllegalArgumentException(
                    "Not formattable: " + chronoType);
            }

            this.steps = new ArrayList<>();
            this.stack = new LinkedList<>();
            this.sectionID = 0;
            this.reservedIndex = -1;
            this.leftPadWidth = 0;
            this.prolepticGregorian = false;

        }

        //~ Methoden ------------------------------------------------------

        /**
         * <p>Returns the associated chronology. </p>
         *
         * @return  Chronology
         */
        /*[deutsch]
         * <p>Liefert die zugeh&ouml;rige Chronologie. </p>
         *
         * @return  Chronology
         */
        public Chronology<T> getChronology() {

            return this.chronology;

        }

        /**
         * <p>Defines an integer format without sign for given
         * chronological element. </p>
         *
         * <p>Equivalent to {@code addInteger(element, minDigits, maxDigits,
         * SignPolicy.SHOW_NEVER}. </p>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     SignPolicy#SHOW_NEVER
         * @see     #addInteger(ChronoElement, int, int, SignPolicy)
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat ohne Vorzeichen f&uuml;r das
         * angegebene chronologische Element. </p>
         *
         * <p>Entspricht {@code addInteger(element, minDigits, maxDigits,
         * SignPolicy.SHOW_NEVER}. </p>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     SignPolicy#SHOW_NEVER
         * @see     #addInteger(ChronoElement, int, int, SignPolicy)
         */
        public Builder<T> addInteger(
            ChronoElement<Integer> element,
            int minDigits,
            int maxDigits
        ) {

            return this.addNumber(
                element,
                false,
                minDigits,
                maxDigits,
                SignPolicy.SHOW_NEVER
            );

        }

        /**
         * <p>Defines an integer format for given chronological
         * element. </p>
         *
         * <p>First a sign is expected (positive or negative) where the
         * last argument {@code signPolicy} controls the output and
         * interpretation. Following rules hold for the sequence of
         * digits to be formatted: </p>
         *
         * <ol><li>PRINT =&gt; If the resulting sequence of digits has
         * less than {@code minDigits} then it will be left-padded with
         * zero digit char until the sequence has {@code minDigits}
         * digits. But if there are more digits than {@code maxDigits}
         * then an {@code IllegalArgumentException} will be thrown. </li>
         *
         * <li>PARSE =&gt; At most {@code maxDigits} chars will be
         * interpreted as digits. If there are less than {@code minDigits}
         * then the text input will be invalid. Note: If there is no
         * strict or smart mode (lax) then the parser will always assume
         * {@code minDigits == 0} and {@code maxDigits = 9}. </li></ol>
         *
         * <p>Example: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainTime.MILLI_OF_SECOND;
         *  int minDigits = 3;
         *  int maxDigits = 6;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addInteger(
         *          element,
         *          minDigits,
         *          maxDigits,
         *          SignPolicy.SHOW_ALWAYS)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainTime.of(12, 0, 0, 12345678)));
         *  // output: +012
         * </pre>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @param   signPolicy      controls output of numeric sign
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat f&uuml;r das angegebene
         * chronologische Element. </p>
         *
         * <p>Zuerst wird das Vorzeichen erwartet (positiv oder negativ).
         * Das Argument {@code signPolicy} regelt hier die Ausgabe und
         * Interpretation. F&uuml;r die Ziffernfolge der zu formatierenden
         * Zahl gilt: </p>
         *
         * <ol><li>PRINT =&gt; Hat die resultierende Ziffernfolge weniger
         * als {@code minDigits} Stellen, wird links mit der Nullziffer
         * aufgef&uuml;llt, bis {@code minDigits} Stellen erreicht sind.
         * Gibt es hingegen mehr als {@code maxDigits} Stellen, wird eine
         * {@code IllegalArgumentException} generiert. </li>
         *
         * <li>PARSE =&gt; Es werden bis zu {@code maxDigits} Zeichen als
         * Ziffern interpretiert. Gibt es aber weniger als {@code minDigits}
         * Stellen, wird die Texteingabe als ung&uuml;ltig angesehen. Zu
         * beachten: Ist ein laxer Parse-Modus angegeben, dann wird
         * unabh&auml;ngig von den hier angegebenen Argumenten stets
         * {@code minDigits == 0} und die Obergrenze von {@code maxDigits = 9}
         * angenommen. </li></ol>
         *
         * <p>Beispiel: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainTime.MILLI_OF_SECOND;
         *  int minDigits = 3;
         *  int maxDigits = 6;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addInteger(
         *          element,
         *          minDigits,
         *          maxDigits,
         *          SignPolicy.SHOW_ALWAYS)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainTime.of(12, 0, 0, 12345678)));
         *  // Ausgabe: +012
         * </pre>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @param   signPolicy      controls output of numeric sign
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        public Builder<T> addInteger(
            ChronoElement<Integer> element,
            int minDigits,
            int maxDigits,
            SignPolicy signPolicy
        ) {

            return this.addNumber(
                element,
                false,
                minDigits,
                maxDigits,
                signPolicy
            );

        }

        /**
         * <p>Defines an integer format for given chronological
         * element. </p>
         *
         * <p>Like {@link #addInteger(ChronoElement, int, int,
         * SignPolicy)} but on long-basis. </p>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-18
         * @param   maxDigits       maximum count of digits in range 1-18
         * @param   signPolicy      controls output of numeric sign
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-18} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat f&uuml;r das angegebene
         * chronologische Element. </p>
         *
         * <p>Wie {@link #addInteger(ChronoElement, int, int,
         * SignPolicy)}, aber auf long-Basis. </p>
         *
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-18
         * @param   maxDigits       maximum count of digits in range 1-18
         * @param   signPolicy      controls output of numeric sign
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-18} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         */
        public Builder<T> addLongNumber(
            ChronoElement<Long> element,
            int minDigits,
            int maxDigits,
            SignPolicy signPolicy
        ) {

            return this.addNumber(
                element,
                false,
                minDigits,
                maxDigits,
                signPolicy
            );

        }

        /**
         * <p>Defines an integer format without sign and with fixed width
         * for given chronological element. </p>
         *
         * <p>Almost equivalent to
         * {@code addInteger(element, digits, digits, SignPolicy.SHOW_NEVER)}
         * but with following important difference: </p>
         *
         * <p>If this method directly follow after other numerical elements
         * then the fixed width defined here will be preserved in preceding
         * elements so parsing of those ancestors will not consume too many
         * digits (<i>adjacent digit parsing</i>). </p>
         *
         * @param   element         chronological element
         * @param   digits          fixed count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code digits} is out of
         *          range {@code 1-9} or if given element is not supported
         *          by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         * @see     SignPolicy#SHOW_NEVER
         * @see     #addInteger(ChronoElement, int, int, SignPolicy)
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat ohne Vorzeichen und mit fester
         * Breite f&uuml;r das angegebene chronologische Element. </p>
         *
         * <p>Entspricht im wesentlichen der Methode
         * {@code addInteger(element, digits, digits, SignPolicy.SHOW_NEVER)}
         * mit folgendem wichtigen Unterschied: </p>
         *
         * <p>Folgt diese Methode direkt nach anderen numerischen Elementen,
         * wird die hier definierte feste Breite beim Parsen vorreserviert,
         * so da&szlig; vorangehende numerische Elemente nicht zuviele
         * Ziffern interpretieren (<i>adjacent digit parsing</i>). </p>
         *
         * @param   element         chronological element
         * @param   digits          fixed count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code digits} is out of
         *          range {@code 1-9} or if given element is not supported
         *          by chronology
         * @see     Chronology#isSupported(ChronoElement)
         * @see     SignPolicy#SHOW_NEVER
         * @see     #addInteger(ChronoElement, int, int, SignPolicy)
         */
        public Builder<T> addFixedInteger(
            ChronoElement<Integer> element,
            int digits
        ) {

            return this.addNumber(
                element,
                true,
                digits,
                digits,
                SignPolicy.SHOW_NEVER
            );

        }

        /**
         * <p>Defines an integer format without sign for given chronological
         * enumeration element. </p>
         *
         * <p>If the element is compatible to the interface
         * {@code NumericalElement} then its value will first converted to
         * an integer else the ordinal number of enum will be used. A sign
         * is never printed or expected. Example: </p>
         *
         * <pre>
         *  ChronoFormatter&lt;PlainDate&gt; formatter =
         *      ChronoFormatter.setUp(PlainDate.class, Locale.US)
         *      .addNumerical(Weekmodel.of(Locale.US).localDayOfWeek(), 1, 1)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainDate.of(2013, 6, 14))); // Freitag
         *  // output: 6 (next to last day of US week)
         * </pre>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     NumericalElement#numerical(java.lang.Object)
         *          NumericalElement.numerical(V)
         * @see     SignPolicy#SHOW_NEVER
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat ohne Vorzeichen f&uuml;r das
         * angegebene chronologische Aufz&auml;hlungselement. </p>
         *
         * <p>Ist das Element kompatibel zum Interface {@code NumericalElement},
         * dann wird auf Basis seiner numerischen Konversion zu einem Integer
         * formatiert, sonst auf Basis seiner Enum-Ordinalzahl. Ein Vorzeichen
         * wird nie ausgegeben oder erwartet. Beispiel: </p>
         *
         * <pre>
         *  ChronoFormatter&lt;PlainDate&gt; formatter =
         *      ChronoFormatter.setUp(PlainDate.class, Locale.US)
         *      .addNumerical(Weekmodel.of(Locale.US).localDayOfWeek(), 1, 1)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainDate.of(2013, 6, 14))); // Freitag
         *  // Ausgabe: 6 (vorletzter Tag der US-Woche)
         * </pre>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   minDigits       minimum count of digits in range 1-9
         * @param   maxDigits       maximum count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any of {@code minDigits} and
         *          {@code maxDigits} are out of range {@code 1-9} or if
         *          {@code maxDigits < minDigits} or if given element is
         *          not supported by chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @see     Chronology#isSupported(ChronoElement)
         * @see     NumericalElement#numerical(java.lang.Object)
         *          NumericalElement.numerical(V)
         * @see     SignPolicy#SHOW_NEVER
         */
        public <V extends Enum<V>> Builder<T> addNumerical(
            ChronoElement<V> element,
            int minDigits,
            int maxDigits
        ) {

            return this.addNumber(
                element,
                false,
                minDigits,
                maxDigits,
                SignPolicy.SHOW_NEVER
            );

        }

        /**
         * <p>Defines an integer format without sign and with fixed width
         * for given chronological enumeration element. </p>
         *
         * <p>Almost equivalent to {@code addNumerical(element, digits, digits)}
         * but with following important difference: </p>
         *
         * <p>If this method directly follow after other numerical elements
         * then the fixed width defined here will be preserved in preceding
         * elements so parsing of those ancestors will not consume too many
         * digits (<i>adjacent digit parsing</i>). </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   digits          fixed count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code digits} is out of
         *          range {@code 1-9} or if given element is not supported
         *          by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         * @see     #addNumerical(ChronoElement, int, int)
         *          addNumerical(ChronoElement, int, int)
         */
        /*[deutsch]
         * <p>Definiert ein Ganzzahlformat ohne Vorzeichen und mit fester Breite
         * f&uuml;r das angegebene chronologische Aufz&auml;hlungselement. </p>
         *
         * <p>Entspricht im wesentlichen der Methode
         * {@code addNumerical(element, digits, digits)} mit
         * folgendem wichtigen Unterschied: </p>
         *
         * <p>Folgt diese Methode direkt nach anderen numerischen Elementen,
         * wird die hier definierte feste Breite beim Parsen vorreserviert,
         * so da&szlig; vorangehende numerische Elemente nicht zuviele
         * Ziffern interpretieren (<i>adjacent digit parsing</i>). </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   digits          fixed count of digits in range 1-9
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code digits} is out of
         *          range {@code 1-9} or if given element is not supported
         *          by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         * @see     #addNumerical(ChronoElement, int, int)
         *          addNumerical(ChronoElement, int, int)
         */
        public <V extends Enum<V>> Builder<T> addFixedNumerical(
            ChronoElement<V> element,
            int digits
        ) {

            return this.addNumber(
                element,
                true,
                digits,
                digits,
                SignPolicy.SHOW_NEVER
            );

        }

        /**
         * <p>Defines a fractional format for given chronological element
         * including a possible decimal separator char but without any
         * integer part by mapping the context-dependent value range to
         * the interval [0.0-1.0). </p>
         *
         * <p>First a leading decimal separator char will be formatted
         * if required by last argument (for example in US the dot, in
         * Germany a comma). Then the fractional digits follow by mean of
         * the formula {@code (value - min) / (max - min + 1)}. Possible
         * gaps like offset-jumps in the value range mapping will be kept.
         * The fractional representation is most suitable for elements
         * with a fixed value range, for example {@code MINUTE_OF_HOUR}
         * or {@code MILLI_OF_SECOND}. </p>
         *
         * <ol><li>PRINT =&gt; If the resulting sequence of digits after
         * the decimal separator char has less than {@code minDigits}
         * then it will be right-padded with zero digit char until there
         * are {@code minDigits} digits. But if there are more than
         * {@code maxDigits} then the sequence of digits will be
         * truncated. In the special case of {@code minDigits == 0}
         * and if the sequence to be formatted has no digits then the
         * diecimal separator char will be left out. </li>
         *
         * <li>PARSE =&gt; At most {@code maxDigits} chars will be
         * interpreted as digits. If there are less than {@code minDigits}
         * then the text input will be invalid. Note: If there is just a
         * lax mode then the parser will always assume {@code minDigits == 0}
         * and {@code maxDigits = 9} unless a fixed width was implicitly
         * specified by setting {@code minDigits == maxDigits} and without
         * decimal separator char (then <i>adjacent digit parsing</i>). </li>
         * </ol>
         *
         * <p>Example: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainTime.MICRO_OF_SECOND;
         *  int minDigits = 3;
         *  int maxDigits = 6;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addFraction(
         *          element,
         *          minDigits,
         *          maxDigits,
         *          true
         *      ).build();
         *  System.out.println(
         *      formatter.format(PlainTime.of(12, 0, 0, 12345678)));
         *  // output in US: .012345
         * </pre>
         *
         * <p>Note: A fractional element must not be directly preceded by
         * another numerical element. </p>
         *
         * @param   element             chronological element
         * @param   minDigits           minimum count of digits after decimal
         *                              separator in range 0-9
         * @param   maxDigits           maximum count of digits after decimal
         *                              separator in range 1-9
         * @param   decimalSeparator    shall decimal separator be visible?
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code minDigits} is out of
         *          range {@code 0-9} or if {@code maxDigits} is out of range
         *          {@code 1-9} or if {@code maxDigits < minDigits} or if
         *          given element is not supported by chronology or its
         *          preparser or if there is already a fractional or decimal
         *          part defined
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        /*[deutsch]
         * <p>Definiert ein Bruchzahlformat f&uuml;r das angegebene
         * chronologische Element inklusive Dezimaltrennzeichen, aber ohne
         * Integerteil, indem der kontextabh&auml;ngige Wertbereich auf das
         * Intervall [0.0-1.0) abgebildet wird. </p>
         *
         * <p>Zuerst wird ein f&uuml;hrendes Dezimaltrennzeichen in
         * lokalisierter Form formatiert, falls mit dem letzten Argument
         * angefordert (zum Beispiel in den USA ein Punkt, in Deutschland
         * ein Komma). Dann folgen die Nachkommastellen mit Hilfe der
         * Formel {@code (value - min) / (max - min + 1)}. Eventuelle
         * L&uuml;cken wie Zeitzonenspr&uuml;nge im Wertbereich bleiben
         * erhalten. Am besten eignet sich die fraktionale Darstellung
         * f&uuml;r Elemente mit einem festen Wertbereich, zum Beispiel
         * {@code MINUTE_OF_HOUR} oder {@code MILLI_OF_SECOND}. </p>
         *
         * <ol><li>PRINT =&gt; Hat die resultierende Ziffernfolge nach dem
         * Dezimaltrennzeichen weniger als {@code minDigits} Stellen, wird
         * rechts mit der Nullziffer aufgef&uuml;llt, bis {@code minDigits}
         * Stellen erreicht sind. Gibt es hingegen mehr als {@code maxDigits}
         * Stellen, wird die Ziffernfolge bei {@code maxDigits} Stellen
         * abgeschnitten. Wird als Sonderfall {@code minDigits == 0} definiert,
         * und hat die Ziffernfolge keine Nachkommastellen, wird auch das
         * Dezimaltrennzeichen weggelassen. </li>
         *
         * <li>PARSE =&gt; Es werden bis zu {@code maxDigits} Zeichen als
         * Ziffern interpretiert. Gibt es aber weniger als {@code minDigits}
         * Stellen, wird die Texteingabe als ung&uuml;ltig angesehen. Zu
         * beachten: Ist nur ein laxer Parse-Modus angegeben, dann wird
         * unabh&auml;ngig von den hier angegebenen Argumenten stets
         * {@code minDigits == 0} und {@code maxDigits == 9} angenommen,
         * es sei denn, es wurde implizit eine feste Breite mittels
         * {@code minDigits == maxDigits} und ohne Dezimaltrennzeichen
         * angegeben (dann gilt <i>adjacent digit parsing</i>). </li>
         * </ol>
         *
         * <p>Beispiel: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainTime.MICRO_OF_SECOND;
         *  int minDigits = 3;
         *  int maxDigits = 6;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addFraction(
         *          element,
         *          minDigits,
         *          maxDigits,
         *          true
         *      ).build();
         *  System.out.println(
         *      formatter.format(PlainTime.of(12, 0, 0, 12345678)));
         *  // Ausgabe in den USA: .012345
         * </pre>
         *
         * <p>Hinweis: Direkt hinter einem fraktionalen Element darf kein
         * anderes numerisches Element folgen. </p>
         *
         * @param   element             chronological element
         * @param   minDigits           minimum count of digits after decimal
         *                              separator in range 0-9
         * @param   maxDigits           maximum count of digits after decimal
         *                              separator in range 1-9
         * @param   decimalSeparator    shall decimal separator be visible?
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code minDigits} is out of
         *          range {@code 0-9} or if {@code maxDigits} is out of range
         *          {@code 1-9} or if {@code maxDigits < minDigits} or if
         *          given element is not supported by chronology or its
         *          preparser or if there is already a fractional or decimal
         *          part defined
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        public Builder<T> addFraction(
            ChronoElement<Integer> element,
            int minDigits,
            int maxDigits,
            boolean decimalSeparator
        ) {

            this.checkElement(element);
            boolean fixedWidth =
                (!decimalSeparator && (minDigits == maxDigits));
            this.ensureOnlyOneFractional(fixedWidth, decimalSeparator);

            FormatProcessor<?> processor =
                new FractionProcessor(
                    element,
                    minDigits,
                    maxDigits,
                    decimalSeparator
                );

            if (
                (this.reservedIndex != -1)
                && fixedWidth
            ) {
                int ri = this.reservedIndex;
                FormatStep numStep = this.steps.get(ri);
                this.addProcessor(processor);
                FormatStep lastStep = this.steps.get(this.steps.size() - 1);

                if (numStep.getSection() == lastStep.getSection()) {
                    this.reservedIndex = ri;
                    this.steps.set(ri, numStep.reserve(minDigits));
                }
            } else {
                this.addProcessor(processor);
            }

            return this;
        }

        /**
         * <p>Defines a fixed unsigned decimal format for given chronological
         * element. </p>
         *
         * <ol><li><p>PRINT =&gt; If the resulting sequence of digits before
         * the decimal separator char has less than {@code precision - scale}
         * digits then it will be left-padded with zero digit chars. Otherwise
         * if there are more integer digits than allowed then an exception will
         * be thrown.</p>
         *
         * <p>If the resulting sequence of digits after the decimal separator
         * is smaller than {@code scale} then the sequence will be right-padded
         * with zero digit chars. Otherwise the sequence of decimal digits will
         * be truncated if necessary. </p></li>
         *
         * <li>PARSE =&gt; Exactly {@code precision} chars will be
         * interpreted as digits in strict mode. Otherwise as many digits are
         * parsed as available and found. </li>
         * </ol>
         *
         * <p>Example: </p>
         * <pre>
         *  ChronoElement&lt;BigDecimal&gt; element = PlainTime.DECIMAL_MINUTE;
         *  int precision = 3;
         *  int scale = 1;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addPattern(&quot;HH:&quot;, PatternType.CLDR)
         *      .addFixedDecimal(element, precision, scale)
         *      .build();
         *  System.out.println(formatter.format(PlainTime.of(12, 8, 30)));
         *  // output: 12:08.5
         * </pre>
         *
         * <p>Note: A decimal element must not be directly preceded by
         * another numerical element. </p>
         *
         * @param   element         chronological element
         * @param   precision       total count of digits {@code >= 2}
         * @param   scale           digits after decimal separator {@code >= 1}
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code precision} is smaller
         *          than {@code 2} or if {@code scale} is smaller than
         *          {@code 1} or if {@code precision <= scale} or if
         *          given element is not supported by chronology or its
         *          preparser or if there is already a fractional or decimal
         *          part defined
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        /*[deutsch]
         * <p>Definiert ein festes Dezimalformat ohne Vorzeichen f&uuml;r das
         * angegebene chronologische Element. </p>
         *
         * <ol><li><p>PRINT =&gt; Wenn die Anzahl der Ziffern vor dem
         * Dezimaltrennzeichen kleiner als {@code precision - scale} ist,
         * dann wird von links mit Null-Ziffern aufgef&uuml;llt. Gibt es
         * andererseits mehr Ziffern als vorgegeben, wird eine Ausnahme
         * geworfen. </p>
         *
         * <p>Ist die Anzahl der Ziffern nach dem Dezimaltrennzeichen kleiner
         * als {@code scale}, dann wird die Sequenz rechts mit Null-Ziffern
         * aufgef&uuml;llt. Andernfalls wird die Sequenz bei Bedarf
         * abgeschnitten. </p></li>
         *
         * <li>PARSE =&gt; Exakt {@code precision} Zeichen werden nebst dem
         * Dezimaltrennzeichen als Ziffern im strikten Modus interpretiert.
         * Andernfalls werden soviele Ziffern interpretiert, wie welche
         * vorhanden sind. </li>
         * </ol>
         *
         * <p>Beispiel: </p>
         * <pre>
         *  ChronoElement&lt;BigDecimal&gt; element = PlainTime.DECIMAL_MINUTE;
         *  int precision = 3;
         *  int scale = 1;
         *
         *  ChronoFormatter&lt;PlainTime&gt; formatter =
         *      ChronoFormatter.setUp(PlainTime.class, Locale.US)
         *      .addPattern(&quot;HH:&quot;, PatternType.CLDR)
         *      .addFixedDecimal(element, precision, scale)
         *      .build();
         *  System.out.println(formatter.format(PlainTime.of(12, 8, 30)));
         *  // Ausgabe: 12:08.5
         * </pre>
         *
         * <p>Hinweis: Direkt hinter einem dezimalen Element darf kein
         * anderes numerisches Element folgen. </p>
         *
         * @param   element         chronological element
         * @param   precision       total count of digits {@code >= 2}
         * @param   scale           digits after decimal separator {@code >= 1}
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if {@code precision} is smaller
         *          than {@code 2} or if {@code scale} is smaller than
         *          {@code 1} or if {@code precision <= scale} or if
         *          given element is not supported by chronology or its
         *          preparser or if there is already a fractional or decimal
         *          part defined
         * @see     Chronology#isSupported(ChronoElement)
         * @see     Attributes#LENIENCY
         */
        public Builder<T> addFixedDecimal(
            ChronoElement<BigDecimal> element,
            int precision,
            int scale
        ) {

            this.checkElement(element);
            this.ensureDecimalDigitsOnlyOnce();

            FormatProcessor<?> processor =
                new DecimalProcessor(element, precision, scale);

            if (this.reservedIndex != -1) {
                int ri = this.reservedIndex;
                FormatStep numStep = this.steps.get(ri);
                this.addProcessor(processor);
                FormatStep lastStep = this.steps.get(this.steps.size() - 1);

                if (numStep.getSection() == lastStep.getSection()) {
                    this.reservedIndex = ri;
                    this.steps.set(ri, numStep.reserve(precision - scale));
                }
            } else {
                this.addProcessor(processor);
            }

            return this;
        }

        /**
         * <p>Defines an ordinal format for given chronological
         * element in english language. </p>
         *
         * <p>An ordinal indicator will be printed or parsed after a
         * given sequence of digits. In English the indicators &quot;st&quot;,
         * &quot;nd&quot;, &quot;rd&quot; and &quot;th&quot; are used
         * dependent on the value of the element. </p>
         *
         * @param   element         chronological element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by the underlying chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @since   1.2
         * @see     Chronology#isSupported(ChronoElement)
         * @see     #addOrdinal(ChronoElement,Map)
         */
        /*[deutsch]
         * <p>Definiert ein Ordinalformat f&uuml;r das angegebene chronologische
         * Element in der englischen Sprache. </p>
         *
         * <p>Ein Ordinal-Indikator wird als Suffix an eine Ziffernfolge
         * angeh&auml;ngt. In Englisch gibt es die Indikatoren &quot;st&quot;,
         * &quot;nd&quot;, &quot;rd&quot; und &quot;th&quot;, welche vom
         * numerischen Wert des Elements abh&auml;ngig sind. </p>
         *
         * @param   element         chronological element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by the underlying chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @since   1.2
         * @see     Chronology#isSupported(ChronoElement)
         * @see     #addOrdinal(ChronoElement,Map)
         */
        public Builder<T> addEnglishOrdinal(ChronoElement<Integer> element) {

            return this.addOrdinalProcessor(element, null);

        }

        /**
         * <p>Defines an ordinal format for given chronological
         * element. </p>
         *
         * <p>An ordinal indicator will be printed or parsed after a
         * given sequence of digits. In English the indicators &quot;st&quot;,
         * &quot;nd&quot;, &quot;rd&quot; and &quot;th&quot; are used
         * dependent on the value of the element. In many other languages a
         * fixed literal can be sufficient (although often context-dependent).
         * This method is necessary if the indicators of a given language
         * depend on the numerical value of the element. </p>
         *
         * <p>Example for French generating HTML-text: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainDate.DAY_OF_MONTH;
         *  Map&lt;PluralCategory, String&gt; indicators =
         *      new HashMap&lt;PluralCategory, String&gt;();
         *  indicators.put(PluralCategory.ONE, "&lt;sup&gt;er&lt;/sup&gt;");
         *  indicators.put(PluralCategory.OTHER, "&lt;sup&gt;e&lt;/sup&gt;");
         *
         *  ChronoFormatter&lt;PlainDate&gt; formatter =
         *      ChronoFormatter.setUp(PlainDate.class, Locale.FRENCH)
         *      .addOrdinal(element, indicators)
         *      .addLiteral(&quot; jour&quot;)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainDate.of(2014, 8, 1)));
         *  // output: 1&lt;sup&gt;er&lt;/sup&gt; jour
         * </pre>
         *
         * <p>Note that dependent on the context other strings can be necessary,
         * for example French also uses feminine forms (for the week etc.). </p>
         *
         * @param   element         chronological element
         * @param   indicators      ordinal indicators to be used as suffixes
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if there is no indicator at least
         *          for the plural category OTHER or if given element is not
         *          supported by the underlying chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @since   1.2
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein Ordinalformat f&uuml;r das angegebene chronologische
         * Element. </p>
         *
         * <p>Ein Ordinal-Indikator wird als Suffix an eine Ziffernfolge
         * angeh&auml;ngt. In Englisch gibt es die Indikatoren &quot;st&quot;,
         * &quot;nd&quot;, &quot;rd&quot; und &quot;th&quot;, welche vom
         * numerischen Wert des Elements abh&auml;ngig sind. In vielen
         * anderen Sprachen ist eine festes Literal ausreichend (obwohl oft
         * kontext-abh&auml;ngig). Diese Methode ist notwendig, wenn die
         * Indikatoren in einer gegebenen Sprache vom numerischen Wert des
         * Elements abh&auml;ngig sind. </p>
         *
         * <p>Beispiel f&uuml;r Franz&ouml;sisch - erzeugt HTML-Text: </p>
         * <pre>
         *  ChronoElement&lt;Integer&gt; element = PlainDate.DAY_OF_MONTH;
         *  Map&lt;PluralCategory, String&gt; indicators =
         *      new HashMap&lt;PluralCategory, String&gt;();
         *  indicators.put(PluralCategory.ONE, "&lt;sup&gt;er&lt;/sup&gt;");
         *  indicators.put(PluralCategory.OTHER, "&lt;sup&gt;e&lt;/sup&gt;");
         *
         *  ChronoFormatter&lt;PlainDate&gt; formatter =
         *      ChronoFormatter.setUp(PlainDate.class, Locale.FRENCH)
         *      .addOrdinal(element, indicators)
         *      .addLiteral(&quot; jour&quot;)
         *      .build();
         *  System.out.println(
         *      formatter.format(PlainDate.of(2014, 8, 1)));
         *  // Ausgabe: 1&lt;sup&gt;er&lt;/sup&gt; jour
         * </pre>
         *
         * <p>Zu beachten ist, da&szlig; abh&auml;ngig vom Kontext andere
         * Textformen notwendig sein k&ouml;nnen. Zum Beispiel kennt die
         * franz&ouml;sische Sprache auch weibliche Formen (etwa f&uuml;r
         * die Woche etc.). </p>
         *
         * @param   element         chronological element
         * @param   indicators      ordinal indicators to be used as suffixes
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if there is no indicator at least
         *          for the plural category OTHER or if given element is not
         *          supported by the underlying chronology or its preparser
         * @throws  IllegalStateException if a numerical element is added
         *          multiple times in a row
         * @since   1.2
         * @see     Chronology#isSupported(ChronoElement)
         */
        public Builder<T> addOrdinal(
            ChronoElement<Integer> element,
            Map<PluralCategory, String> indicators
        ) {

            if (indicators == null) {
                throw new NullPointerException("Missing ordinal indicators.");
            }

            return this.addOrdinalProcessor(element, indicators);

        }

        /**
         * <p>Defines a literal element with exactly one char. </p>
         *
         * <p>Usually the literal char is a punctuation mark or a
         * letter symbol. Decimal digits as literal chars are not
         * recommended, especially not after preceding numerical
         * elements. </p>
         *
         * @param   literal         single literal char
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if the char represents
         *          a non-printable ASCII-char
         */
        /*[deutsch]
         * <p>Definiert ein Literalelement mit genau einem festen Zeichen. </p>
         *
         * <p>In der Regel handelt es sich um ein Interpunktionszeichen oder
         * ein Buchstabensymbol. Dezimalziffern als Literal werden nicht
         * empfohlen, besonders nicht nach vorhergehenden numerischen
         * Elementen. </p>
         *
         * @param   literal         single literal char
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if the char represents
         *          a non-printable ASCII-char
         */
        public Builder<T> addLiteral(char literal) {

            return this.addLiteral(String.valueOf(literal));

        }

        /**
         * <p>Defines a literal element with exactly one char which can also be an alternative char
         * during parsing. </p>
         *
         * <p>Usually the literal char is a punctuation mark or a
         * letter symbol. Decimal digits as literal chars are not
         * recommended, especially not after preceding numerical
         * elements. </p>
         *
         * @param   literal         single literal char (preferred in printing)
         * @param   alt             alternative literal char for parsing
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any char represents a non-printable ASCII-char
         * @since   3.1
         */
        /*[deutsch]
         * <p>Definiert ein Literalelement mit genau einem festen Zeichen, das beim Parsen
         * auch ein Alternativzeichen sein kann. </p>
         *
         * <p>In der Regel handelt es sich um ein Interpunktionszeichen oder
         * ein Buchstabensymbol. Dezimalziffern als Literal werden nicht
         * empfohlen, besonders nicht nach vorhergehenden numerischen
         * Elementen. </p>
         *
         * @param   literal         single literal char (preferred in printing)
         * @param   alt             alternative literal char for parsing
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any char represents a non-printable ASCII-char
         * @since   3.1
         */
        public Builder<T> addLiteral(
            char literal,
            char alt
        ) {

            this.addProcessor(new LiteralProcessor(literal, alt));
            return this;

        }

        /**
         * <p>Defines a literal element with any chars. </p>
         *
         * <p>Usually the literal char sequence consists of punctuation
         * marks or letter symbols. Leading decimal digits as literal
         * chars are not recommended, especially not after preceding
         * numerical elements. </p>
         *
         * @param   literal         literal char sequence
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given literal is empty
         *          or starts with a non-printable ASCII-char
         */
        /*[deutsch]
         * <p>Definiert ein Literalelement mit beliebigen Zeichen. </p>
         *
         * <p>In der Regel handelt es sich um Interpunktionszeichen oder
         * Buchstabensymbole. F&uuml;hrende Dezimalziffern als Literal werden
         * nicht empfohlen, besonders nicht nach vorhergehenden numerischen
         * Elementen. </p>
         *
         * @param   literal         literal char sequence
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given literal is empty
         *          or starts with a non-printable ASCII-char
         */
        public Builder<T> addLiteral(String literal) {

            this.addProcessor(new LiteralProcessor(literal));
            return this;

        }

        /**
         * <p>Defines a literal element with a char which will be searched
         * in given format attribute. </p>
         *
         * <p>A localized decimal separator char as literal will be possible
         * if the argument is equal to {@link Attributes#DECIMAL_SEPARATOR}.
         * Note: If given format attribute does not exist at runtime then
         * the formatting will fail. </p>
         *
         * @param   attribute       attribute defining a literal char
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Definiert ein Literalelement mit einem Zeichen, das in einem
         * Formatattribut gesucht wird. </p>
         *
         * <p>Ein lokalisiertes Dezimaltrennzeichen als Literal ist auch
         * m&ouml;glich, wenn als Argument {@link Attributes#DECIMAL_SEPARATOR}
         * angegeben wird. Hinweis: Existiert das Formatattribut nicht zur
         * Laufzeit, wird die Formatierung scheitern. </p>
         *
         * @param   attribute       attribute defining a literal char
         * @return  this instance for method chaining
         */
        public Builder<T> addLiteral(AttributeKey<Character> attribute) {

            this.addProcessor(new LiteralProcessor(attribute));
            return this;

        }

        /**
         * <p>Defines a sequence of optional white space. </p>
         *
         * <p>If printed a space char will be used. In parsing however,
         * any white space of arbitrary length will be ignored and
         * skipped. </p>
         *
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Definiert optionale nicht-anzeigbare Zeichen. </p>
         *
         * <p>Beim Formatieren wird ein Leerzeichen ausgegeben, beim Parsen
         * eine beliebig lange Kette von nicht-anzeigbaren Zeichen ignoriert
         * und &uuml;bersprungen. </p>
         *
         * @return  this instance for method chaining
         */
        public Builder<T> addIgnorableWhitespace() {

            this.addProcessor(IgnorableWhitespaceProcessor.SINGLETON);
            return this;

        }

        /**
         * <p>Processes given format pattern of given pattern type to a
         * sequence of format elements. </p>
         *
         * <p>The letters a-z and A-Z are treated as format symbols. The
         * square brackets &quot;[&quot; and &quot;]&quot; define an
         * optional section which can be nested, too.. The chars
         * &quot;#&quot;, &quot;{&quot; and &quot;}&quot; are reserved
         * for the future. All other chars will be interpreted as literals.
         * If a reserved char shall be treated as literal then it must be
         * escaped by the apostroph &quot;'&quot;. The apostroph itself
         * can be treated as literal by double apostroph. </p>
         *
         * <p>For exact interpretation and description of format symbols
         * see the implementations of interface {@code ChronoPattern}. </p>
         *
         * @param   formatPattern   pattern of symbols to be used in formatting
         * @param   patternType     type of pattern how to interprete symbols
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if resolving of pattern fails
         * @see     PatternType
         */
        /*[deutsch]
         * <p>Verarbeitet ein beliebiges Formatmuster des angegebenen Typs. </p>
         *
         * <p>Als Formatsymbole werden die Buchstaben a-z und A-Z erkannt. Die
         * eckigen Klammern &quot;[&quot; und &quot;]&quot; leiten eine
         * optionale Sektion ein, die auch verschachtelt werden darf. Die
         * Zeichen &quot;#&quot;, &quot;{&quot; und &quot;}&quot; sind f&uuml;r
         * die Zukunft reserviert. Alle anderen Zeichen werden als Literale
         * interpretiert. Falls ein reserviertes Zeichen auch als Literal
         * gelten soll, mu&szlig; es mittels eines Apostrophs &quot;'&quot;
         * gekennzeichnet werden (ESCAPE). Das Apostroph selbst wird durch
         * Verdoppelung als Literal interpretiert. </p>
         *
         * <p>Zur genauen Interpretation der Formatsymbole sei auf die
         * Implementierungen des Interface {@code ChronoPattern} verwiesen. </p>
         *
         * @param   formatPattern   pattern of symbols to be used in formatting
         * @param   patternType     type of pattern how to interprete symbols
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if resolving of pattern fails
         * @see     PatternType
         */
        public Builder<T> addPattern(
            String formatPattern,
            PatternType patternType
        ) {

            if (patternType == null) {
                throw new NullPointerException("Missing pattern type.");
            }

            Map<ChronoElement<?>, ChronoElement<?>> replacement = Collections.emptyMap();
            int n = formatPattern.length();
            Locale loc = this.locale;

            if (!this.stack.isEmpty()) {
                loc = this.stack.getLast().getLocale();
            }

            for (int i = 0; i < n; i++) {
                char c = formatPattern.charAt(i);

                if (isSymbol(c)) {
                    int start = i++;

                    while ((i < n) && formatPattern.charAt(i) == c) {
                        i++;
                    }

                    Map<ChronoElement<?>, ChronoElement<?>> map =
                        patternType.registerSymbol(this, loc, c, i - start);

                    if (replacement.isEmpty()) {
                        replacement = map;
                    } else {
                        Map<ChronoElement<?>, ChronoElement<?>> tmp = new HashMap<>(replacement);
                        tmp.putAll(map);
                        replacement = tmp;
                    }

                    i--; // Schleifenzähler nicht doppelt inkrementieren
                } else if (c == '\'') {
                    int start = i++;

                    while (i < n) {
                        if (formatPattern.charAt(i) == '\'') {
                            if (
                                (i + 1 < n)
                                && (formatPattern.charAt(i + 1) == '\'')
                            ) {
                                i++;
                            } else {
                                break;
                            }
                        }
                        i++;
                    }

                    if (i >= n) {
                        throw new IllegalArgumentException(
                            "String literal in pattern not closed: "
                            + formatPattern);
                    }

                    if (start + 1 == i) {
                        this.addLiteral('\'');
                    } else {
                        String s = formatPattern.substring(start + 1, i);
                        this.addLiteral(s.replace("''", "'"));
                    }
                } else if (c == '[') {
                    this.startOptionalSection();
                } else if (c == ']') {
                    this.endSection();
                } else if ((c == '#') || (c == '{') || (c == '}')) {
                    throw new IllegalArgumentException(
                        "Pattern contains reserved character: '" + c + "'");
                } else {
                    this.addLiteral(c);
                }
            }

            if (!replacement.isEmpty()) {
                int len = this.steps.size();

                for (int i = 0; i < len; i++) {
                    FormatStep step = this.steps.get(i);
                    ChronoElement<?> element = step.getProcessor().getElement();

                    if (this.chronology.isRegistered(element)) {
                        if (replacement.containsKey(element)) {
                            ChronoElement<?> newElement = replacement.get(element);
                            this.steps.set(i, step.updateElement(newElement));
                        }
                    }
                }
            }

            return this;

        }

        /**
         * <p>Defines a text format for given chronological element. </p>
         *
         * @param   element         chronological text element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein Textformat f&uuml;r das angegebene Element. </p>
         *
         * @param   element         chronological text element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        public Builder<T> addText(TextElement<?> element) {

            this.checkElement(element);
            this.addProcessor(TextProcessor.create(element));
            return this;

        }

        /**
         * <p>Defines a text format for given chronological element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein Textformat f&uuml;r das angegebene Element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology
         * @see     Chronology#isSupported(ChronoElement)
         */
        public <V extends Enum<V>> Builder<T> addText(
            ChronoElement<V> element
        ) {

            this.checkElement(element);

            if (element instanceof TextElement) {
                TextElement<?> te = TextElement.class.cast(element);
                this.addProcessor(TextProcessor.create(te));
            } else {
                // String-Ressource ist enum.toString()
                Map<V, String> empty = Collections.emptyMap();
                this.addProcessor(new LookupProcessor<>(element, empty));
            }

            return this;

        }

        /**
         * <p>Defines a text format for given chronological element with
         * user-defined string resources. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   lookup          text resources for lookup
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein Textformat f&uuml;r das angegebene Element mit
         * benutzerdefinierten String-Ressourcen. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   lookup          text resources for lookup
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        public <V extends Enum<V>> Builder<T> addText(
            ChronoElement<V> element,
            Map<V, String> lookup
        ) {

            this.checkElement(element);
            this.addProcessor(new LookupProcessor<>(element, lookup));
            return this;

        }

        /**
         * <p>Defines a customized format element for given chronological
         * element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   formatter       customized formatter object as delegate
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein benutzerdefiniertes Format f&uuml;r das angegebene
         * chronologische Element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   formatter       customized formatter object as delegate
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        public <V extends ChronoEntity<V>> Builder<T> addCustomized(
            ChronoElement<V> element,
            final ChronoFormatter<V> formatter
        ) {

            return this.addCustomized(element, formatter, formatter);

        }

        /**
         * <p>Defines a customized format element for given chronological
         * element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   printer         customized printer
         * @param   parser          customized parser
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        /*[deutsch]
         * <p>Definiert ein benutzerdefiniertes Format f&uuml;r das angegebene
         * chronologische Element. </p>
         *
         * @param   <V> generic type of element values
         * @param   element         chronological element
         * @param   printer         customized printer
         * @param   parser          customized parser
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology or its preparser
         * @see     Chronology#isSupported(ChronoElement)
         */
        public <V> Builder<T> addCustomized(
            ChronoElement<V> element,
            ChronoPrinter<V> printer,
            ChronoParser<V> parser
        ) {

            this.checkElement(element);
            this.startSection(Attributes.TRAILING_CHARACTERS, true);
            this.addProcessor(new CustomizedProcessor<>(element, printer, parser));
            this.endSection();
            return this;

        }

        /**
         * <p>Defines a special format element for a two-digit-year. </p>
         *
         * <p>It is possible to specify a pivot year by setting the
         * attribute {@code Attributes.PIVOT_YEAR} to a meaningful year.
         * If this attribute is missing then Time4J will set the year
         * twenty years after now as pivot year by default. </p>
         *
         * <p>If this format element is directly preceded by other numerical
         * elements with variable width then the fixed width of 2 will be
         * preserved such that the preceding elements will not consume too
         * many digits (<i>adjacent digit parsing</i>). Otherwise this
         * format element can also parse more than two digits if there is
         * no strict mode with the consequence that the parsed year will be
         * interpreted as absolute full year. </p>
         *
         * @param   element     year element (name must start with the
         *                      prefix &quot;YEAR&quot;)
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology
         * @see     Attributes#PIVOT_YEAR
         */
        /*[deutsch]
         * <p>Definiert eine zweistellige Jahresangabe. </p>
         *
         * <p>Die Angabe eines Kippjahres ist mit Hilfe des Attributs
         * {@code Attributes.PIVOT_YEAR} m&ouml;glich und ist beim
         * Interpretieren zweistelliger Jahresangaben von Bedeutung.
         * Fehlt das Attribut, wird Time4J standardm&auml;&szlig; 20 Jahre
         * in der Zukunft das Kippjahr setzen. </p>
         *
         * <p>Folgt diese Methode direkt nach anderen numerischen Elementen
         * mit variabler Breite, wird die hier definierte feste Breite beim
         * Parsen vorreserviert, so da&szlig; vorangehende numerische Elemente
         * nicht zuviele Ziffern interpretieren (<i>adjacent digit parsing</i>).
         * Andernfalls k&ouml;nnen auch mehr als zwei Ziffern interpretiert
         * werden, sofern kein strikter Modus vorliegt mit der Folge, da&szlig;
         * eine solche Jahreszahl als absolutes volles Jahr interpretiert
         * wird. </p>
         *
         * @param   element     year element (name must start with the
         *                      prefix &quot;YEAR&quot;)
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given element is not
         *          supported by chronology
         * @see     Attributes#PIVOT_YEAR
         */
        public Builder<T> addTwoDigitYear(ChronoElement<Integer> element) {

            this.checkElement(element);
            this.checkAfterDecimalDigits(element);
            FormatProcessor<?> processor = new TwoDigitYearProcessor(element);

            if (this.reservedIndex == -1) {
                this.addProcessor(processor);
                this.reservedIndex = this.steps.size() - 1;
            } else {
                int ri = this.reservedIndex;
                FormatStep numStep = this.steps.get(ri);
                this.startSection(Attributes.LENIENCY, Leniency.STRICT);
                this.addProcessor(processor);
                this.endSection();
                FormatStep lastStep = this.steps.get(this.steps.size() - 1);

                if (numStep.getSection() == lastStep.getSection()) {
                    this.reservedIndex = ri;
                    this.steps.set(ri, numStep.reserve(2));
                }
            }

            return this;

        }

        /**
         * <p>Adds a timezone identifier. </p>
         *
         * <p>Parsing of a timezone ID is case-sensitive. All timezone IDs
         * which will be provided by {@link Timezone#getAvailableIDs()}
         * will be supported - with the exception of old IDs like
         * &quot;Asia/Riyadh87&quot; or &quot;CST6CDT&quot; which contain
         * some digits. Offset-IDs like the canonical form of
         * {@code ZonalOffset} or &quot;GMT&quot; are supported, too.
         * An exceptional case are again deprecated IDs like
         * &quot;Etc/GMT+12&quot;. </p>
         *
         * @return  this instance for method chaining
         * @throws  IllegalStateException wenn die zugrundeliegende Chronologie
         *          nicht dem Typ {@link net.time4j.base.UnixTime} entspricht
         */
        /*[deutsch]
         * <p>F&uuml;gt eine Zeitzonen-ID hinzu. </p>
         *
         * <p>Die Gro&szlig;- und Kleinschreibung der Zeitzonen-ID ist
         * relevant. Unterst&uuml;tzt werden alle IDs, die von
         * {@link Timezone#getAvailableIDs()} geliefert werden - mit
         * Ausnahme von alten IDs wie &quot;Asia/Riyadh87&quot; oder
         * &quot;CST6CDT&quot;, die Ziffern enthalten. Offset-IDs wie
         * die kanonische Form von {@code ZonalOffset} oder &quot;GMT&quot;
         * werden ebenfalls unterst&uuml;tzt. Eine Ausnahme sind hier
         * veraltete IDs wie &quot;Etc/GMT+12&quot;. </p>
         *
         * @return  this instance for method chaining
         * @throws  IllegalStateException wenn die zugrundeliegende Chronologie
         *          nicht dem Typ {@link net.time4j.base.UnixTime} entspricht
         */
        public Builder<T> addTimezoneID() {

            Class<?> chronoType = this.getChronology().getChronoType();

            if (UnixTime.class.isAssignableFrom(chronoType)) {
                this.addProcessor(TimezoneIDProcessor.INSTANCE);
                return this;
            } else {
                throw new IllegalStateException(
                    "Only unix timestamps can have a timezone id.");
            }

        }

        /**
         * <p>Adds a short localized timezone name (an abbreviation). </p>
         *
         * <p>Dependent on the current locale, the preferred timezone IDs
         * in a country will be determined first. The parsing of
         * timezone names is case-sensitive. </p>
         *
         * <p>Note that Time4J will try to find a unique mapping from names to
         * IDs for US in smart parsing mode. However, this is only an imperfect
         * approximation to current practice. A counter example is Phoenix
         * which does not observe daylight savings although it has the same
         * name &quot;MST&quot; as Denver. </p>
         *
         * @return  this instance for method chaining
         * @see     #addShortTimezoneName(Set)
         */
        /*[deutsch]
         * <p>F&uuml;gt die Abk&uuml;rzung eines Zeitzonennamens hinzu. </p>
         *
         * <p>Mit Hilfe der aktuellen L&auml;ndereinstellung werden zuerst
         * die bevorzugten Zeitzonen-IDs bestimmt. Die Gro&szlig;- und
         * Kleinschreibung der Zeitzonennamen wird beachtet. </p>
         *
         * <p>Hinweis: Time4J versucht das Beste, um eine eindeutige Abbildung
         * von Namen auf IDs f&uuml;r die USA im smart-Modus zu finden. Aber
         * das ist nur eine nicht perfekte Ann&auml;herung an die aktuelle
         * Praxis. Ein Gegenbeispiel ist Phoenix, das keine Sommerzeit kennt,
         * obwohl es den gleichen Namen &quot;MST&quot; wie Denver hat. </p>
         *
         * @return  this instance for method chaining
         * @see     #addShortTimezoneName(Set)
         */
        public Builder<T> addShortTimezoneName() {

            this.addProcessor(new TimezoneNameProcessor(true));
            return this;

        }

        /**
         * <p>Adds a long localized timezone name. </p>
         *
         * <p>Dependent on the current locale, the preferred timezone IDs
         * in a country will be determined first. The parsing of
         * timezone names is case-sensitive. </p>
         *
         * <p>Note that Time4J will try to find a unique mapping from names to
         * IDs for US in smart parsing mode. However, this is only an imperfect
         * approximation to current practice. A counter example is Phoenix
         * which does not observe daylight savings although it has the same
         * name &quot;Mountain Standard Time&quot; as Denver. </p>
         *
         * @return  this instance for method chaining
         * @see     #addLongTimezoneName(Set)
         */
        /*[deutsch]
         * <p>F&uuml;gt einen langen Zeitzonennamen hinzu. </p>
         *
         * <p>Mit Hilfe der aktuellen L&auml;ndereinstellung werden zuerst
         * die bevorzugten Zeitzonen-IDs bestimmt. Die Gro&szlig;- und
         * Kleinschreibung der Zeitzonennamen wird beachtet. </p>
         *
         * <p>Hinweis: Time4J versucht das Beste, um eine eindeutige Abbildung
         * von Namen auf IDs f&uuml;r die USA im smart-Modus zu finden. Aber
         * das ist nur eine nicht perfekte Ann&auml;herung an die aktuelle
         * Praxis. Ein Gegenbeispiel ist Phoenix, das keine Sommerzeit kennt,
         * obwohl es den gleichen Namen &quot;Mountain Standard Time&quot;
         * wie Denver hat. </p>
         *
         * @return  this instance for method chaining
         * @see     #addLongTimezoneName(Set)
         */
        public Builder<T> addLongTimezoneName() {

            this.addProcessor(new TimezoneNameProcessor(false));
            return this;

        }

        /**
         * <p>Adds a short localized timezone name (an abbreviation). </p>
         *
         * <p>Parsing of timezone names is case-sensitive. </p>
         *
         * @param   preferredZones  preferred timezone ids for resolving
         *                          duplicates
         * @return  this instance for method chaining
         * @see     #addLongTimezoneName(Set)
         */
        /*[deutsch]
         * <p>F&uuml;gt die Abk&uuml;rzung eines Zeitzonennamens hinzu. </p>
         *
         * <p>Die Gro&szlig;- und Kleinschreibung der Zeitzonennamen wird
         * beachtet. </p>
         *
         * @param   preferredZones  preferred timezone ids for resolving
         *                          duplicates
         * @return  this instance for method chaining
         * @see     #addLongTimezoneName(Set)
         */
        public Builder<T> addShortTimezoneName(Set<TZID> preferredZones) {

            this.addProcessor(new TimezoneNameProcessor(true, preferredZones));
            return this;

        }

        /**
         * <p>Adds a long localized timezone name. </p>
         *
         * <p>Parsing of timezone names is case-sensitive. </p>
         *
         * @param   preferredZones  preferred timezone ids for resolving
         *                          duplicates
         * @return  this instance for method chaining
         * @see     #addShortTimezoneName(Set)
         */
        /*[deutsch]
         * <p>F&uuml;gt einen langen Zeitzonennamen hinzu. </p>
         *
         * <p>Die Gro&szlig;- und Kleinschreibung der Zeitzonennamen wird
         * beachtet. </p>
         *
         * @param   preferredZones  preferred timezone ids for resolving
         *                          duplicates
         * @return  this instance for method chaining
         * @see     #addShortTimezoneName(Set)
         */
        public Builder<T> addLongTimezoneName(Set<TZID> preferredZones) {

            this.addProcessor(new TimezoneNameProcessor(false, preferredZones));
            return this;

        }

        /**
         * <p>Adds a timezone offset in typical ISO-8601-notation. </p>
         *
         * <p>The offset format is &quot;&#x00B1;HH:mm&quot; or in case of
         * zero offset simply &quot;Z&quot;. Equivalent to the expression
         * {@code addTimezoneOffset(DisplayMode.MEDIUM, true,
         * Collections.singletonList("Z"))}. </p>
         *
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>F&uuml;gt einen Zeitzonen-Offset in typischer ISO-8601-Notation
         * hinzu. </p>
         *
         * <p>Das Offset-Format ist &quot;&#x00B1;HH:mm&quot; oder im Fall
         * des Null-Offsets einfach &quot;Z&quot;. Entspricht dem Ausdruck
         * {@code addTimezoneOffset(DisplayMode.MEDIUM, true,
         * Collections.singletonList("Z"))}. </p>
         *
         * @return  this instance for method chaining
         */
        public Builder<T> addTimezoneOffset() {

            return this.addTimezoneOffset(
                DisplayMode.MEDIUM, true, Collections.singletonList("Z"));

        }

        /**
         * <p>Adds a timezone offset in canonical notation. </p>
         *
         * <p>This format element is also applicable on chronological
         * entities without timezone reference like {@code PlainTime}
         * provided that a timezone offset is set as format attribute.
         * Dependent on given arguments following formats are
         * defined: </p>
         *
         * <div style="margin-top:5px;">
         * <table border="1">
         * <caption>Legend</caption>
         * <tr>
         *  <th>&nbsp;</th>
         *  <th>SHORT</th>
         *  <th>MEDIUM</th>
         *  <th>LONG</th>
         *  <th>FULL</th>
         * </tr>
         * <tr>
         *  <td>basic</td>
         *  <td>&#x00B1;HH[mm]</td>
         *  <td>&#x00B1;HHmm</td>
         *  <td>&#x00B1;HHmm[ss[.{fraction}]]</td>
         *  <td>&#x00B1;HHmmss[.{fraction}]</td>
         * </tr>
         * <tr>
         *  <td>extended</td>
         *  <td>&#x00B1;HH[:mm]</td>
         *  <td>&#x00B1;HH:mm</td>
         *  <td>&#x00B1;HH:mm[:ss[.{fraction}]]</td>
         *  <td>&#x00B1;HH:mm:ss[.{fraction}]</td>
         * </tr>
         * </table>
         * </div>
         *
         * <p>Notes: All components given in square brackets are optional.
         * They will only appear if they are different from {@code 0}.
         * A fractional second part with 9 digits is always optional
         * and is only possible in case of a longitudinal offset. The modes
         * SHORT and MEDIUM correspond to ISO-8601 where an offset should
         * only have hours and minutes. </p>
         *
         * <p>The third argument determines what kind of text should be
         * interpreted as zero offset. The formatted output always uses
         * the first list entry while parsing expects any list entries. </p>
         *
         * @param   precision       display mode of offset format
         * @param   extended        extended or basic ISO-8601-mode
         * @param   zeroOffsets     list of replacement texts if offset is zero
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any replacement text consists
         *          of white-space only or if given replacement list is empty
         * @see     ChronoEntity#getTimezone()
         */
        /*[deutsch]
         * <p>F&uuml;gt einen Zeitzonen-Offset in kanonischer Notation
         * hinzu. </p>
         *
         * <p>Anwendbar ist dieses Formatierungselement auch auf lokale
         * Typen ohne Zeitzonenbezug wie z.B. {@code PlainTime}, setzt dann
         * aber voraus, da&szlig; ein Zeitzonen-Offset als Attribut des
         * {@code ChronoFormatter} mitgegeben wird. In Abh&auml;ngigkeit
         * von den Argumenten sind folgende Formate definiert: </p>
         *
         * <div style="margin-top:5px;">
         * <table border="1">
         * <caption>Legende</caption>
         * <tr>
         *  <th>&nbsp;</th>
         *  <th>SHORT</th>
         *  <th>MEDIUM</th>
         *  <th>LONG</th>
         *  <th>FULL</th>
         * </tr>
         * <tr>
         *  <td>basic</td>
         *  <td>&#x00B1;HH[mm]</td>
         *  <td>&#x00B1;HHmm</td>
         *  <td>&#x00B1;HHmm[ss[.{fraction}]]</td>
         *  <td>&#x00B1;HHmmss[.{fraction}]</td>
         * </tr>
         * <tr>
         *  <td>extended</td>
         *  <td>&#x00B1;HH[:mm]</td>
         *  <td>&#x00B1;HH:mm</td>
         *  <td>&#x00B1;HH:mm[:ss[.{fraction}]]</td>
         *  <td>&#x00B1;HH:mm:ss[.{fraction}]</td>
         * </tr>
         * </table>
         * </div>
         *
         * <p>Hinweise: Die in eckigen Klammern angegebenen Komponenten
         * sind optional, erscheinen also nur, wenn sie von {@code 0}
         * verschieden sind. Ein fraktionaler stets 9-stelliger Sekundenteil
         * ist immer optional und nur dann m&ouml;glich, wenn ein longitudinaler
         * Offset verwendet wird. Die Genauigkeitsangaben SHORT und MEDIUM
         * entsprechen der ISO-8601-Notation, in der nur Stunden und Minuten
         * formatiert werden. </p>
         *
         * <p>Das dritte Argument legt fest, was als Null-Offset interpretiert
         * werden soll. Die formatierte Ausgabe benutzt immer den ersten
         * Listeneintrag. </p>
         *
         * @param   precision       display mode of offset format
         * @param   extended        extended or basic ISO-8601-mode
         * @param   zeroOffsets     list of replacement texts if offset is zero
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if any replacement text consists
         *          of white-space only or if given replacement list is empty
         * @see     ChronoEntity#getTimezone()
         */
        public Builder<T> addTimezoneOffset(
            DisplayMode precision,
            boolean extended,
            List<String> zeroOffsets
        ) {

            this.addProcessor(
                new TimezoneOffsetProcessor(precision, extended, zeroOffsets));
            return this;

        }

        /**
         * <p>Adds a timezone offset in short localized notation. </p>
         *
         * <p>This format element is also applicable on chronological
         * entities without timezone reference like {@code PlainTime}
         * provided that a timezone offset is set as format attribute.
         * The format &quot;GMT&#x00B1;H[:mm]&quot; will be used. </p>
         *
         * <p>Notes: The minute component given in square brackets is
         * optional in short format and appears only if it is different
         * from {@code 0}. The GMT-prefix can also be like &quot;UTC&quot;
         * or &quot;UT&quot; when parsing. A localized GMT-notation is
         * possible provided that the resource files
         * &quot;iso8601.properties&quot; have an entry with the key
         * &quot;prefixGMTOffset&quot;. </p>
         *
         * @return  this instance for method chaining
         * @see     ChronoEntity#getTimezone()
         * @see     #addLongLocalizedOffset()
         */
        /*[deutsch]
         * <p>F&uuml;gt einen Zeitzonen-Offset in kurzer lokalisierter Notation
         * hinzu. </p>
         *
         * <p>Anwendbar ist dieses Formatierungselement auch auf lokale
         * Typen ohne Zeitzonenbezug wie z.B. {@code PlainTime}, setzt dann
         * aber voraus, da&szlig; ein Zeitzonen-Offset als Attribut des
         * {@code ChronoFormatter} mitgegeben wird. Als Format wird
         * &quot;GMT&#x00B1;H[:mm]&quot; verwendet. </p>
         *
         * <p>Hinweise: Die in eckigen Klammern angegebene Minutenkomponente
         * ist optional, erscheint also nur, wenn sie von {@code 0} verschieden
         * ist. Das GMT-Pr&auml;fix darf beim Parsen auch als &quot;UTC&quot;
         * oder &quot;UT&quot; vorliegen. Au&szlig;erdem ist eine lokalisierte
         * GMT-Notation m&ouml;glich, indem in den Ressourcendateien
         * &quot;iso8601.properties&quot; ein Eintrag mit dem Schl&uuml;ssel
         * &quot;prefixGMTOffset&quot; vorhanden ist. </p>
         *
         * @return  this instance for method chaining
         * @see     ChronoEntity#getTimezone()
         * @see     #addLongLocalizedOffset()
         */
        public Builder<T> addShortLocalizedOffset() {

            this.addProcessor(new LocalizedGMTProcessor(true));
            return this;

        }

        /**
         * <p>Adds a timezone offset in long localized notation. </p>
         *
         * <p>This format element is also applicable on chronological
         * entities without timezone reference like {@code PlainTime}
         * provided that a timezone offset is set as format attribute.
         * The format &quot;GMT&#x00B1;HH:mm&quot; will be used. </p>
         *
         * <p>Notes: The GMT-prefix can also be like &quot;UTC&quot;
         * or &quot;UT&quot; when parsing. A localized GMT-notation is
         * possible provided that the resource files
         * &quot;iso8601.properties&quot; have an entry with the key
         * &quot;prefixGMTOffset&quot;. </p>
         *
         * @return  this instance for method chaining
         * @see     ChronoEntity#getTimezone()
         * @see     #addShortLocalizedOffset()
         */
        /*[deutsch]
         * <p>F&uuml;gt einen Zeitzonen-Offset in langer lokalisierter Notation
         * hinzu. </p>
         *
         * <p>Anwendbar ist dieses Formatierungselement auch auf lokale
         * Typen ohne Zeitzonenbezug wie z.B. {@code PlainTime}, setzt dann
         * aber voraus, da&szlig; ein Zeitzonen-Offset als Attribut des
         * {@code ChronoFormatter} mitgegeben wird. Als Format wird
         * &quot;GMT&#x00B1;HH:mm&quot; verwendet. </p>
         *
         * <p>Hinweise: Das GMT-Pr&auml;fix darf beim Parsen auch als
         * &quot;UTC&quot; oder &quot;UT&quot; vorliegen. Au&szlig;erdem
         * ist eine lokalisierte GMT-Notation m&ouml;glich, indem in den
         * Ressourcendateien &quot;iso8601.properties&quot; ein Eintrag mit
         * dem Schl&uuml;ssel &quot;prefixGMTOffset&quot; vorhanden ist. </p>
         *
         * @return  this instance for method chaining
         * @see     ChronoEntity#getTimezone()
         * @see     #addShortLocalizedOffset()
         */
        public Builder<T> addLongLocalizedOffset() {

            this.addProcessor(new LocalizedGMTProcessor(false));
            return this;

        }

        /**
         * <p>Defines for the next format element of the same section so
         * many pad chars until the element width has reached the width
         * specified. </p>
         *
         * <p>Note: This method will be ignored if it is directly followed
         * by a new section or if the current section is closed
         * or if there are no more format elements. </p>
         *
         * @param   width   fixed width of following format step
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given width is negative
         * @see     Attributes#PAD_CHAR
         * @see     #padPrevious(int)
         */
        /*[deutsch]
         * <p>Definiert zum n&auml;chsten Element der gleichen Sektion soviele
         * F&uuml;llzeichen, bis die Elementbreite die angegebene Breite
         * erreicht hat. </p>
         *
         * <p>Zu beachten: Diese Methode wird ignoriert, wenn unmittelbar
         * danach eine neue Sektion gestartet, die aktuelle Sektion beendet
         * oder gar kein Element mehr hinzugef&uuml;gt wird. </p>
         *
         * @param   width   fixed width of following format step
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given width is negative
         * @see     Attributes#PAD_CHAR
         * @see     #padPrevious(int)
         */
        public Builder<T> padNext(int width) {

            if (width < 0) {
                throw new IllegalArgumentException(
                    "Negative pad width: " + width);
            } else if (width > 0) {
                this.leftPadWidth = width;
            }

            return this;

        }

        /**
         * <p>Defines for the previous format element of the same
         * section so many pad chars until the element width has
         * reached the width specified. </p>
         *
         * @param   width   fixed width of previous format step
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given width is negative
         * @see     Attributes#PAD_CHAR
         * @see     #padNext(int)
         */
        /*[deutsch]
         * <p>Definiert zum vorherigen Element  der gleichen Sektion soviele
         * F&uuml;llzeichen, bis die Elementbreite die angegebene Breite
         * erreicht hat. </p>
         *
         * @param   width   fixed width of previous format step
         * @return  this instance for method chaining
         * @throws  IllegalArgumentException if given width is negative
         * @see     Attributes#PAD_CHAR
         * @see     #padNext(int)
         */
        public Builder<T> padPrevious(int width) {

            if (width < 0) {
                throw new IllegalArgumentException(
                    "Negative pad width: " + width);
            } else if (
                !this.steps.isEmpty()
                && (width > 0)
            ) {
                int index = this.steps.size() - 1;
                FormatStep lastStep = this.steps.get(index);
                int currentSection = 0;

                if (!this.stack.isEmpty()) {
                    currentSection = this.stack.getLast().getSection();
                }

                if (currentSection == lastStep.getSection()) {
                    this.steps.set(index, lastStep.pad(0, width));
                }
            }

            return this;

        }

        /**
         * <p>Starts a new optional section where errors in parsing will
         * not cause an exception but just be ignored. </p>
         *
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen optionalen Abschnitt, in dem Fehler beim
         * Interpretieren nicht zum Abbruch f&uuml;hren, sondern nur ignoriert
         * werden. </p>
         *
         * @return  this instance for method chaining
         */
        public Builder<T> startOptionalSection() {

            return this.startOptionalSection(null);

        }

        /**
         * <p>Starts a new optional section where errors in parsing will
         * not cause an exception but just be ignored. </p>
         *
         * @param   printCondition  optional condition for printing
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen optionalen Abschnitt, in dem Fehler beim
         * Interpretieren nicht zum Abbruch f&uuml;hren, sondern nur ignoriert
         * werden. </p>
         *
         * @param   printCondition  optional condition for printing
         * @return  this instance for method chaining
         */
        public Builder<T> startOptionalSection(final ChronoCondition<ChronoDisplay> printCondition) {

            this.resetPadding();
            Attributes.Builder ab = new Attributes.Builder(this.chronology);
            AttributeSet previous = null;
            ChronoCondition<ChronoDisplay> cc = null;

            if (!this.stack.isEmpty()) {
                previous = this.stack.getLast();
                ab.setAll(previous.getAttributes());
                cc = previous.getCondition();
            }

            int newLevel = getLevel(previous) + 1;
            int newSection = ++this.sectionID;

            if (printCondition != null) {
                final ChronoCondition<ChronoDisplay> old = cc;

                if (old == null) {
                    cc = printCondition;
                } else {
                    cc =
                        context -> (
                            old.test(context)
                            && printCondition.test(context));
                }
            }

            AttributeSet as = new AttributeSet(ab.build(), this.locale, newLevel, newSection, cc, null);
            this.stack.addLast(as);
            return this;

        }

        /**
         * <p>Starts a new section with given sectional attribute. </p>
         *
         * <p>The new section takes over all attributes of current section
         * if available. Sectional attributes cannot be overridden by the
         * default attributes of {@code ChronoFormatter}. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen Abschnitt mit dem angegebenen sektionalen
         * boolean-Attribut. </p>
         *
         * <p>Der neue Abschnitt &uuml;bernimmt alle Attribute des aktuellen
         * Attributabschnitts, falls vorhanden. Sektionale Attribute k&ouml;nnen
         * durch Standard-Attribute des {@code ChronoFormatter} nicht
         * &uuml;bersteuert werden. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        public Builder<T> startSection(
            AttributeKey<Boolean> key,
            boolean value
        ) {

            checkAttribute(key);
            this.resetPadding();

            AttributeSet as;
            Attributes.Builder ab;

            if (this.stack.isEmpty()) {
                ab = new Attributes.Builder(this.chronology);
                as = new AttributeSet(ab.set(key, value).build(), this.locale);
            } else {
                AttributeSet old = this.stack.getLast();
                ab = new Attributes.Builder();
                ab.setAll(old.getAttributes());
                ab.set(key, value);
                as = old.withAttributes(ab.build());
            }

            this.stack.addLast(as);
            return this;

        }

        /**
         * <p>Starts a new section with given sectional attribute. </p>
         *
         * <p>The new section takes over all attributes of current section
         * if available. Sectional attributes cannot be overridden by the
         * default attributes of {@code ChronoFormatter}. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen Abschnitt mit dem angegebenen sektionalen
         * int-Attribut. </p>
         *
         * <p>Der neue Abschnitt &uuml;bernimmt alle Attribute des aktuellen
         * Attributabschnitts, falls vorhanden. Sektionale Attribute k&ouml;nnen
         * durch Standard-Attribute des {@code ChronoFormatter} nicht
         * &uuml;bersteuert werden. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        public Builder<T> startSection(
            AttributeKey<Integer> key,
            int value
        ) {

            checkAttribute(key);
            this.resetPadding();

            AttributeSet as;
            Attributes.Builder ab;

            if (this.stack.isEmpty()) {
                ab = new Attributes.Builder(this.chronology);
                as = new AttributeSet(ab.set(key, value).build(), this.locale);
            } else {
                AttributeSet old = this.stack.getLast();
                ab = new Attributes.Builder();
                ab.setAll(old.getAttributes());
                ab.set(key, value);
                as = old.withAttributes(ab.build());
            }

            this.stack.addLast(as);
            return this;

        }

        /**
         * <p>Starts a new section with given sectional attribute. </p>
         *
         * <p>The new section takes over all attributes of current section
         * if available. Sectional attributes cannot be overridden by the
         * default attributes of {@code ChronoFormatter}. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen Abschnitt mit dem angegebenen sektionalen
         * char-Attribut. </p>
         *
         * <p>Der neue Abschnitt &uuml;bernimmt alle Attribute des aktuellen
         * Attributabschnitts, falls vorhanden. Sektionale Attribute k&ouml;nnen
         * durch Standard-Attribute des {@code ChronoFormatter} nicht
         * &uuml;bersteuert werden. </p>
         *
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        public Builder<T> startSection(
            AttributeKey<Character> key,
            char value
        ) {

            checkAttribute(key);
            this.resetPadding();

            AttributeSet as;
            Attributes.Builder ab;

            if (this.stack.isEmpty()) {
                ab = new Attributes.Builder(this.chronology);
                as = new AttributeSet(ab.set(key, value).build(), this.locale);
            } else {
                AttributeSet old = this.stack.getLast();
                ab = new Attributes.Builder();
                ab.setAll(old.getAttributes());
                ab.set(key, value);
                as = old.withAttributes(ab.build());
            }

            this.stack.addLast(as);
            return this;

        }

        /**
         * <p>Starts a new section with given sectional attribute. </p>
         *
         * <p>The new section takes over all attributes of current section
         * if available. Sectional attributes cannot be overridden by the
         * default attributes of {@code ChronoFormatter}. </p>
         *
         * @param   <A> generic type of attribute (enum-based)
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        /*[deutsch]
         * <p>Startet einen neuen Abschnitt mit dem angegebenen sektionalen
         * enum-Attribut. </p>
         *
         * <p>Der neue Abschnitt &uuml;bernimmt alle Attribute des aktuellen
         * Attributabschnitts, falls vorhanden. Sektionale Attribute k&ouml;nnen
         * durch Standard-Attribute des {@code ChronoFormatter} nicht
         * &uuml;bersteuert werden. </p>
         *
         * @param   <A> generic type of attribute (enum-based)
         * @param   key     attribute key
         * @param   value   attribute value
         * @return  this instance for method chaining
         */
        public <A extends Enum<A>> Builder<T> startSection(
            AttributeKey<A> key,
            A value
        ) {

            checkAttribute(key);
            this.resetPadding();

            AttributeSet as;
            Attributes.Builder ab;

            if (this.stack.isEmpty()) {
                ab = new Attributes.Builder(this.chronology);
                as = new AttributeSet(ab.set(key, value).build(), this.locale);
            } else {
                AttributeSet old = this.stack.getLast();
                ab = new Attributes.Builder();
                ab.setAll(old.getAttributes());
                ab.set(key, value);
                as = old.withAttributes(ab.build());
            }

            this.stack.addLast(as);
            return this;

        }

        /**
         * <p>Removes the last sectional attribute. </p>
         *
         * @return  this instance for method chaining
         * @throws  java.util.NoSuchElementException if there is no section
         *          which was started with {@code startSection()}
         * @see     #startSection(AttributeKey, boolean)
         * @see     #startSection(AttributeKey, Enum)
         * @see     #startSection(AttributeKey, int)
         * @see     #startSection(AttributeKey, char)
         */
        /*[deutsch]
         * <p>Entfernt das letzte sektionale Attribut. </p>
         *
         * @return  this instance for method chaining
         * @throws  java.util.NoSuchElementException if there is no section
         *          which was started with {@code startSection()}
         * @see     #startSection(AttributeKey, boolean)
         * @see     #startSection(AttributeKey, Enum)
         * @see     #startSection(AttributeKey, int)
         * @see     #startSection(AttributeKey, char)
         */
        public Builder<T> endSection() {

            this.stack.removeLast();
            this.resetPadding();
            return this;

        }

        /**
         * <p>Finishes the build and creates a new {@code ChronoFormatter}. </p>
         *
         * @return  new {@code ChronoFormatter}-instance
         */
        /*[deutsch]
         * <p>Schlie&szlig;t den Build-Vorgang ab und erstellt ein neues
         * Zeitformat. </p>
         *
         * @return  new {@code ChronoFormatter}-instance
         */
        public ChronoFormatter<T> build() {

            ChronoFormatter<T> formatter =
                new ChronoFormatter<>(
                    this.chronology,
                    this.locale,
                    this.steps
                );

            if (this.prolepticGregorian) {
                formatter = formatter.with(ChronoHistory.PROLEPTIC_GREGORIAN);
            }

            return formatter;

        }

        // Spezialmethode, um das CLDR-Symbol u immer als proleptic-iso-year zu schützen
        Builder<T> addProlepticIsoYear(
            int count,
            SignPolicy signPolicy
        ) {

            return this.addNumber( PlainDate.YEAR, false, count, 9, signPolicy, true);

        }

        // Spezialmethode, um das CLDR-Symbol u immer als proleptic-iso-year zu schützen
        Builder<T> addProlepticIsoYearWithTwoDigits() {

            return this.addNumber(PlainDate.YEAR, true, 2, 2, SignPolicy.SHOW_NEVER, true);

        }

        // Spezialmethode für PatternType.THREETEN
        void setProlepticGregorian() {

            this.prolepticGregorian = true;

        }

        private <V> Builder<T> addNumber(
            ChronoElement<V> element,
            boolean fixedWidth,
            int minDigits,
            int maxDigits,
            SignPolicy signPolicy
        ) {

            return this.addNumber(element, fixedWidth, minDigits, maxDigits, signPolicy, false);

        }

        private <V> Builder<T> addNumber(
            ChronoElement<V> element,
            boolean fixedWidth,
            int minDigits,
            int maxDigits,
            SignPolicy signPolicy,
            boolean protectedMode
        ) {

            this.checkElement(element);
            FormatStep last = this.checkAfterDecimalDigits(element);

            NumberProcessor<V> np =
                new NumberProcessor<>(
                    element,
                    fixedWidth,
                    minDigits,
                    maxDigits,
                    signPolicy,
                    protectedMode
                );

            if (fixedWidth) {
                if (this.reservedIndex == -1) {
                    this.addProcessor(np);
                } else {
                    int ri = this.reservedIndex;
                    FormatStep numStep = this.steps.get(ri);
                    this.addProcessor(np);
                    FormatStep lastStep = this.steps.get(this.steps.size() - 1);

                    if (numStep.getSection() == lastStep.getSection()) {
                        this.reservedIndex = ri;
                        this.steps.set(ri, numStep.reserve(minDigits));
                    }
                }
            } else if (
                (last != null)
                && last.isNumerical()
            ) {
                throw new IllegalStateException(
                    "Numerical element with variable width can't be inserted "
                    + "after another numerical element. "
                    + "Consider \"addFixedXXX()\" instead.");
            } else {
                this.addProcessor(np);
                this.reservedIndex = this.steps.size() - 1;
            }

            return this;

        }

        private Builder<T> addOrdinalProcessor(
            ChronoElement<Integer> element,
            Map<PluralCategory, String> indicators
        ) {

            this.checkElement(element);
            FormatStep last = this.checkAfterDecimalDigits(element);
            OrdinalProcessor p = new OrdinalProcessor(element, indicators);

            if (
                (last != null)
                && last.isNumerical()
            ) {
                throw new IllegalStateException(
                    "Ordinal element with variable width can't be inserted "
                    + "after another numerical element.");
            } else {
                this.addProcessor(p);
            }

            return this;

        }

        private void addProcessor(FormatProcessor<?> processor) {

            this.reservedIndex = -1;
            AttributeSet attrs = null;
            int level = 0;
            int section = 0;

            if (!this.stack.isEmpty()) {
                attrs = this.stack.getLast();
                level = attrs.getLevel();
                section = attrs.getSection();
            }

            FormatStep step = new FormatStep(processor, level, section, attrs);

            if (this.leftPadWidth > 0) {
                step = step.pad(this.leftPadWidth, 0);
                this.leftPadWidth = 0;
            }

            this.steps.add(step);

        }

        private static int getLevel(AttributeSet attributes) {

            if (attributes == null) {
                return 0;
            }

            return attributes.getLevel();

        }

        private static void checkAttribute(AttributeKey<?> key) {

            if (key.name().charAt(0) == '_') {
                throw new IllegalArgumentException(
                    "Internal attribute not allowed: " + key.name());
            }

        }

        private void resetPadding() {

            this.leftPadWidth = 0;

        }

        private static boolean isSymbol(char c) {

            return (
                ((c >= 'A') && (c <= 'Z'))
                || ((c >= 'a') && (c <= 'z'))
            );

        }

        private void checkElement(ChronoElement<?> element) {

            ChronoFormatter.checkElement(this.chronology, element);

        }

        private void ensureDecimalDigitsOnlyOnce() {

            for (FormatStep step : this.steps) {
                if (step.isDecimal()) {
                    throw new IllegalArgumentException(
                        "Cannot define more than one element"
                        + " with decimal digits.");
                }
            }

        }

        private void ensureOnlyOneFractional(
            boolean fixedWidth,
            boolean decimalSeparator
        ) {

            this.ensureDecimalDigitsOnlyOnce();

            if (
                !fixedWidth
                && !decimalSeparator
                && (this.reservedIndex != -1)
            ) {
                throw new IllegalArgumentException(
                    "Cannot add fractional element with variable width "
                    + "after another numerical element with variable width.");
            }

        }

        private FormatStep checkAfterDecimalDigits(ChronoElement<?> element) {

            FormatStep last = (
                this.steps.isEmpty()
                ? null
                : this.steps.get(this.steps.size() - 1)
            );

            if (last == null) {
                return null;
            }

            if (last.isDecimal()) {
                throw new IllegalStateException(
                    element.name()
                    + " can't be inserted after an element"
                    + " with decimal digits.");
            }

            return last;

        }

    }

    /**
     * @serial  exclude
     */
    @SuppressWarnings("serial") // Not serializable!
    private static class TraditionalFormat<T extends ChronoEntity<T>>
        extends Format {

        //~ Statische Felder/Initialisierungen ----------------------------

        private static final Map<String, DateFormat.Field> FIELD_MAP;

        static {
            Map<String, DateFormat.Field> map = new HashMap<>();
            map.put("YEAR", DateFormat.Field.YEAR);
            map.put("YEAR_OF_ERA", DateFormat.Field.YEAR);
            map.put("YEAR_OF_WEEKDATE", DateFormat.Field.YEAR);
            map.put("WEEK_OF_YEAR", DateFormat.Field.WEEK_OF_YEAR);
            map.put("WEEK_OF_MONTH", DateFormat.Field.WEEK_OF_MONTH);
            map.put("BOUNDED_WEEK_OF_YEAR", DateFormat.Field.WEEK_OF_YEAR);
            map.put("BOUNDED_WEEK_OF_MONTH", DateFormat.Field.WEEK_OF_MONTH);
            map.put("MONTH_OF_YEAR", DateFormat.Field.MONTH);
            map.put("MONTH_AS_NUMBER", DateFormat.Field.MONTH);
            map.put("HISTORIC_MONTH", DateFormat.Field.MONTH);
            map.put("WEEKDAY_IN_MONTH", DateFormat.Field.DAY_OF_WEEK_IN_MONTH);
            map.put("SECOND_OF_MINUTE", DateFormat.Field.SECOND);
            map.put("MINUTE_OF_HOUR", DateFormat.Field.MINUTE);
            map.put("MILLI_OF_SECOND", DateFormat.Field.MILLISECOND);
            map.put("DIGITAL_HOUR_OF_DAY", DateFormat.Field.HOUR_OF_DAY0);
            map.put("DIGITAL_HOUR_OF_AMPM", DateFormat.Field.HOUR0);
            map.put("CLOCK_HOUR_OF_DAY", DateFormat.Field.HOUR_OF_DAY1);
            map.put("CLOCK_HOUR_OF_AMPM", DateFormat.Field.HOUR1);
            map.put("AM_PM_OF_DAY", DateFormat.Field.AM_PM);
            map.put("DAY_OF_MONTH", DateFormat.Field.DAY_OF_MONTH);
            map.put("HISTORIC_DAY_OF_MONTH", DateFormat.Field.DAY_OF_MONTH);
            map.put("DAY_OF_WEEK", DateFormat.Field.DAY_OF_WEEK);
            map.put("LOCAL_DAY_OF_WEEK", DateFormat.Field.DAY_OF_WEEK);
            map.put("DAY_OF_YEAR", DateFormat.Field.DAY_OF_YEAR);
            map.put("TIMEZONE_ID", DateFormat.Field.TIME_ZONE);
            map.put("ERA", DateFormat.Field.ERA);
            FIELD_MAP = Collections.unmodifiableMap(map);
        }

        //~ Instanzvariablen ----------------------------------------------

        private final ChronoFormatter<T> formatter;

        //~ Konstruktoren -------------------------------------------------

        TraditionalFormat(ChronoFormatter<T> formatter) {
            super();

            this.formatter = formatter;

        }

        //~ Methoden ------------------------------------------------------

        @Override
        public StringBuffer format(
            Object obj,
            StringBuffer toAppendTo,
            FieldPosition pos
        ) {

            pos.setBeginIndex(0);
            pos.setEndIndex(0);

            try {
                AttributeQuery attrs = this.formatter.globalAttributes;
                String calendarType =
                    attrs.get(Attributes.CALENDAR_TYPE, ISO_CALENDAR_TYPE);
                T formattable =
                    this.formatter.getChronology().getChronoType().cast(obj);
                Set<ElementPosition> positions =
                    this.formatter.print(formattable, toAppendTo, attrs);

                if (calendarType.equals(ISO_CALENDAR_TYPE)) {
                    for (ElementPosition position : positions) {
                        DateFormat.Field field = toField(position.getElement());
                        if (
                            (field != null)
                            && (field.equals(pos.getFieldAttribute())
                                || ((field.getCalendarField() == pos.getField())
                                    && (pos.getField() != -1))
                                || (field.equals(DateFormat.Field.TIME_ZONE)
                                    && (pos.getField() == DateFormat.TIMEZONE_FIELD))
                                || (field.equals(DateFormat.Field.HOUR_OF_DAY1)
                                    && (pos.getField() == DateFormat.HOUR_OF_DAY1_FIELD))
                                || (field.equals(DateFormat.Field.HOUR1)
                                    && (pos.getField() == DateFormat.HOUR1_FIELD))
                            )
                        ) {
                            pos.setBeginIndex(position.getStartIndex());
                            pos.setEndIndex(position.getEndIndex());
                            break;
                        }
                    }
                }

                return toAppendTo;

            } catch (ClassCastException cce) {
                throw new IllegalArgumentException(
                    "Not formattable: " + obj, cce);
            } catch (IOException ioe) {
                throw new IllegalArgumentException(
                    "Cannot print object: " + obj, ioe);
            }
        }

        @Override
        public AttributedCharacterIterator formatToCharacterIterator(Object o) {

            String calendarType =
                this.formatter.globalAttributes.get(Attributes.CALENDAR_TYPE, ISO_CALENDAR_TYPE);

            if (calendarType.equals(ISO_CALENDAR_TYPE)) {
                try {
                    StringBuilder toAppendTo = new StringBuilder();
                    T formattable =
                        this.formatter.getChronology().getChronoType().cast(o);
                    Set<ElementPosition> positions =
                        this.formatter.print(formattable, toAppendTo);
                    AttributedString as =
                        new AttributedString(toAppendTo.toString());
                    for (ElementPosition position : positions) {
                        DateFormat.Field field = toField(position.getElement());
                        if (field != null) {
                            as.addAttribute(
                                field,
                                field,
                                position.getStartIndex(),
                                position.getEndIndex());
                        }
                    }
                    return as.getIterator();
                } catch (ClassCastException cce) {
                    throw new IllegalArgumentException(
                        "Not formattable: " + o, cce);
                }
            }

            return super.formatToCharacterIterator(o);

        }

        @Override
        public Object parseObject(
            String source,
            ParsePosition pos
        ) {

            ParseLog status = new ParseLog(pos.getIndex());
            T result = this.formatter.parse(source, status);

            if (result == null) {
                pos.setErrorIndex(status.getErrorIndex());
            } else {
                pos.setIndex(status.getPosition());
            }

            return result;

        }

        private static DateFormat.Field toField(ChronoElement<?> element) {

            return FIELD_MAP.get(element.name());

        }

    }

}
