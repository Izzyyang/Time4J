/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2015 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (NumberProcessor.java) is part of project Time4J.
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

import net.time4j.engine.AttributeQuery;
import net.time4j.engine.ChronoDisplay;
import net.time4j.engine.ChronoElement;
import net.time4j.format.Attributes;
import net.time4j.format.Leniency;
import net.time4j.format.NumericalElement;

import java.io.IOException;
import java.util.Map;
import java.util.Set;


/**
 * <p>Ganzzahl-Formatierung eines chronologischen Elements. </p>
 *
 * @param   <V> generic type of element values (Integer, Long or Enum)
 * @author  Meno Hochschild
 * @since   3.0
 * @doctags.concurrency <immutable>
 */
final class NumberProcessor<V>
    implements FormatProcessor<V> {

    //~ Instanzvariablen --------------------------------------------------

    private final ChronoElement<V> element;
    private final boolean fixedWidth;
    private final int minDigits;
    private final int maxDigits;
    private final SignPolicy signPolicy;
    private final boolean protectedMode;

    //~ Konstruktoren -----------------------------------------------------

    /**
     * <p>Konstruiert eine neue Instanz. </p>
     *
     * @param   element         element to be formatted
     * @param   fixedWidth      fixed-width-mode
     * @param   minDigits       minimum count of digits
     * @param   maxDigits       maximum count of digits
     * @param   signPolicy      sign policy
     * @param   protectedMode   allow replacement?
     * @throws  IllegalArgumentException in case of inconsistencies
     */
    NumberProcessor(
        ChronoElement<V> element,
        boolean fixedWidth,
        int minDigits,
        int maxDigits,
        SignPolicy signPolicy,
        boolean protectedMode
    ) {
        super();

        this.element = element;
        this.fixedWidth = fixedWidth;
        this.minDigits = minDigits;
        this.maxDigits = maxDigits;
        this.signPolicy = signPolicy;
        this.protectedMode = protectedMode;

        if (element == null) {
            throw new NullPointerException("Missing element.");
        } else if (signPolicy == null) {
            throw new NullPointerException("Missing sign policy.");
        } else if (minDigits < 1) {
            throw new IllegalArgumentException(
                "Not positive: " + minDigits);
        } else if (minDigits > maxDigits) {
            throw new IllegalArgumentException(
                "Max smaller than min: " + maxDigits + " < " + minDigits);
        } else if (
            fixedWidth
            && (minDigits != maxDigits)
        ) {
            throw new IllegalArgumentException(
                "Variable width in fixed-width-mode: "
                + maxDigits + " != " + minDigits);
        } else if (
            fixedWidth
            && (signPolicy != SignPolicy.SHOW_NEVER)
        ) {
            throw new IllegalArgumentException(
                "Sign policy must be SHOW_NEVER in fixed-width-mode.");
        }

        int scale = this.getScale();

        if (minDigits > scale) {
            throw new IllegalArgumentException(
                "Min digits out of range: " + minDigits);
        } else if (maxDigits > scale) {
            throw new IllegalArgumentException(
                "Max digits out of range: " + maxDigits);
        }

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public void print(
        ChronoDisplay formattable,
        Appendable buffer,
        AttributeQuery attributes,
        Set<ElementPosition> positions, // optional
        FormatStep step
    ) throws IOException {

        Class<V> type = this.element.getType();
        V value = formattable.get(this.element);
        boolean negative = false;
        String digits;

        if (type == Integer.class) {
            int v = Integer.class.cast(value).intValue();
            negative = (v < 0);
            digits = (
                (v == Integer.MIN_VALUE)
                ? "2147483648"
                : Integer.toString(Math.abs(v))
            );
        } else if (type == Long.class) {
            long v = Long.class.cast(value).longValue();
            negative = (v < 0);
            digits = (
                (v == Long.MIN_VALUE)
                ? "9223372036854775808"
                : Long.toString(Math.abs(v))
            );
        } else if (Enum.class.isAssignableFrom(type)) {
            int v = -1;
            if (this.element instanceof NumericalElement) {
                v = ((NumericalElement<V>) this.element).numerical(value);
                negative = (v < 0);
            } else {
                for (Object e : type.getEnumConstants()) {
                    if (e.equals(value)) {
                        v = Enum.class.cast(e).ordinal();
                        break;
                    }
                }
                if (v == -1) {
                    throw new AssertionError(
                        "Enum broken: " + value + " / " + type.getName());
                }
            }
            digits = (
                (v == Integer.MIN_VALUE)
                ? "2147483648"
                : Integer.toString(Math.abs(v))
            );
        } else {
            throw new IllegalArgumentException(
                "Not formattable: " + this.element);
        }

        if (digits.length() > this.maxDigits) {
            throw new IllegalArgumentException(
                "Element " + this.element.name()
                + " cannot be printed as the value " + value
                + " exceeds the maximum width of " + this.maxDigits + ".");
        }

        char zeroDigit =
            step.getAttribute(
                Attributes.ZERO_DIGIT,
                attributes,
                Character.valueOf('0'))
            .charValue();

        if (zeroDigit != '0') {
            int diff = zeroDigit - '0';
            char[] characters = digits.toCharArray();

            for (int i = 0; i < characters.length; i++) {
                characters[i] = (char) (characters[i] + diff);
            }

            digits = new String(characters);
        }

        int start = -1;
        int printed = 0;

        if (buffer instanceof CharSequence) {
            start = ((CharSequence) buffer).length();
        }

        if (negative) {
            if (this.signPolicy == SignPolicy.SHOW_NEVER) {
                throw new IllegalArgumentException(
                    "Negative value not allowed according to sign policy.");
            } else {
                buffer.append('-');
                printed++;
            }
        } else {
            switch (this.signPolicy) {
                case SHOW_ALWAYS:
                    buffer.append('+');
                    printed++;
                    break;
                case SHOW_WHEN_BIG_NUMBER:
                    if (digits.length() > this.minDigits) {
                        buffer.append('+');
                        printed++;
                    }
                    break;
                default:
                    // no-op
            }
        }

        for (int i = 0, n = this.minDigits - digits.length(); i < n; i++) {
            buffer.append(zeroDigit);
            printed++;
        }

        buffer.append(digits);
        printed += digits.length();

        if (
            (start != -1)
            && (printed > 0)
            && (positions != null)
        ) {
            positions.add(
                new ElementPosition(this.element, start, start + printed));
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

        Leniency leniency =
            step.getAttribute(Attributes.LENIENCY, attributes, Leniency.SMART);

        int effectiveMin = 1;
        int effectiveMax = this.getScale();

        if (
            this.fixedWidth
            || !leniency.isLax()
        ) {
            effectiveMin = this.minDigits;
            effectiveMax = this.maxDigits;
        }

        int len = text.length();
        int start = status.getPosition();
        int pos = start;

        int protectedChars =
            step.getAttribute(
                Attributes.PROTECTED_CHARACTERS,
                attributes,
                0
            ).intValue();

        if (protectedChars > 0) {
            len -= protectedChars;
        }

        if (pos >= len) {
            status.setError(pos, "Missing digits for: " + this.element.name());
            status.setWarning();
            return;
        }

        boolean negative = false;
        char sign = text.charAt(pos);

        if (
            (sign == '-')
            || (sign == '+')
        ) {
            if (
                (this.signPolicy == SignPolicy.SHOW_NEVER)
                && (this.fixedWidth || leniency.isStrict())
            ) {
                status.setError(
                    start,
                    "Sign not allowed due to sign policy.");
                return;
            } else if (
                (this.signPolicy == SignPolicy.SHOW_WHEN_NEGATIVE)
                && (sign == '+')
                && leniency.isStrict()
            ) {
                status.setError(
                    start,
                    "Positive sign not allowed due to sign policy.");
                return;
            }
            negative = (sign == '-');
            pos++;
            start++;
        } else if (
            (this.signPolicy == SignPolicy.SHOW_ALWAYS)
            && leniency.isStrict()
        ) {
            status.setError(start, "Missing sign of number.");
            return;
        }

        if (pos >= len) {
            status.setError(
                start,
                "Missing digits for: " + this.element.name());
            return;
        }

        char zeroDigit =
            step.getAttribute(
                Attributes.ZERO_DIGIT,
                attributes,
                Character.valueOf('0')
            ).charValue();

        int reserved = step.getReserved();

        if (
            !this.fixedWidth
            && (reserved > 0)
            && (protectedChars <= 0)
        ) {
            int digitCount = 0;

            // Wieviele Ziffern hat der ganze Ziffernblock?
            for (int i = pos; i < len; i++) {
                int digit = text.charAt(i) - zeroDigit;

                if ((digit >= 0) && (digit <= 9)) {
                    digitCount++;
                } else {
                    break;
                }
            }

            effectiveMax = Math.min(effectiveMax, digitCount - reserved);
        }

        int minPos = pos + effectiveMin;
        int maxPos = Math.min(len, pos + effectiveMax);
        long total = 0;
        boolean first = true;

        while (pos < maxPos) {
            int digit = text.charAt(pos) - zeroDigit;

            if ((digit >= 0) && (digit <= 9)) {
                total = total * 10 + digit;
                pos++;
                first = false;
            } else if (first) {
                status.setError(start, "Digit expected.");
                return;
            } else {
                break;
            }
        }

        if (
            (pos < minPos)
            && (first || this.fixedWidth || !leniency.isLax())
        ) {
            status.setError(
                start,
                "Not enough digits found for: " + this.element.name());
            return;
        }

        if (negative) {
            if (
                (total == 0)
                && leniency.isStrict()
            ) {
                status.setError(start - 1, "Negative zero is not allowed.");
                return;
            }

            total = -total;
        } else if (
            (this.signPolicy == SignPolicy.SHOW_WHEN_BIG_NUMBER)
            && leniency.isStrict()
        ) {
            if (
                (sign == '+')
                && (pos <= minPos)
            ) {
                status.setError(
                    start - 1,
                    "Positive sign only allowed for big number.");
            } else if (
                (sign != '+')
                && (pos > minPos)
            ) {
                status.setError(
                    start,
                    "Positive sign must be present for big number.");
            }
        }

        Object value = null;
        Class<V> type = this.element.getType();

        if (type == Integer.class) {
            value = Integer.valueOf((int) total);
        } else if (type == Long.class) {
            value = Long.valueOf(total);
        } else if (Enum.class.isAssignableFrom(type)) {
            if (this.element instanceof NumericalElement) { // Normalfall
                NumericalElement<V> ne = (NumericalElement<V>) this.element;
                for (Object e : type.getEnumConstants()) {
                    if (ne.numerical(type.cast(e)) == total) {
                        value = e;
                        break;
                    }
                }
            } else {
                for (Object e : type.getEnumConstants()) { // Ausweichoption
                    if (Enum.class.cast(e).ordinal() == total) {
                        value = e;
                        break;
                    }
                }
            }

            if (value == null) {
                status.setError(
                    ((sign == '-') || (sign == '+') ? start - 1 : start),
                    "["
                        + this.element.name()
                        + "] No enum found for value: "
                        + total);
                return;
            }
        } else {
            throw new IllegalArgumentException(
                "Not parseable: " + this.element);
        }

        parsedResult.put(this.element, value);
        status.setPosition(pos);

    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        } else if (obj instanceof NumberProcessor) {
            NumberProcessor<?> that = (NumberProcessor<?>) obj;
            return (
                this.element.equals(that.element)
                && (this.fixedWidth == that.fixedWidth)
                && (this.minDigits == that.minDigits)
                && (this.maxDigits == that.maxDigits)
                && (this.signPolicy == that.signPolicy)
                && (this.protectedMode == that.protectedMode)
            );
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {

        return (
            7 * this.element.hashCode()
            + 31 * (this.minDigits + this.maxDigits * 10)
        );

    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(64);
        sb.append(this.getClass().getName());
        sb.append("[element=");
        sb.append(this.element.name());
        sb.append(", fixed-width-mode=");
        sb.append(this.fixedWidth);
        sb.append(", min-digits=");
        sb.append(this.minDigits);
        sb.append(", max-digits=");
        sb.append(this.maxDigits);
        sb.append(", sign-policy=");
        sb.append(this.signPolicy);
        sb.append(']');
        return sb.toString();

    }

    @Override
    public ChronoElement<V> getElement() {

        return this.element;

    }

    @Override
    public FormatProcessor<V> withElement(ChronoElement<V> element) {

        if (
            this.protectedMode
            || (this.element == element)
        ) {
            return this;
        }

        return new NumberProcessor<V>(
            element,
            this.fixedWidth,
            this.minDigits,
            this.maxDigits,
            this.signPolicy,
            false
        );

    }

    @Override
    public boolean isNumerical() {

        return true;

    }

    private int getScale() {

        return ((this.element.getType() == Long.class) ? 18 : 9);

    }

}
