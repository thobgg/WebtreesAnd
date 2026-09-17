package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.data.GedcomDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GedcomDateTest {
    @Test
    fun germanInput() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12.3.1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12.03.1890"))
        assertEquals("MAR 1890", GedcomDate.fromInput("3.1890"))
        assertEquals("1890", GedcomDate.fromInput(" 1890 "))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12. März 1890"))
        assertEquals("DEC 1901", GedcomDate.fromInput("Dezember 1901"))
    }

    @Test
    fun qualifiers() {
        assertEquals("ABT 1850", GedcomDate.fromInput("um 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("ca. 1850"))
        assertEquals("BEF 1900", GedcomDate.fromInput("vor 1900"))
        assertEquals("AFT 1 MAY 1900", GedcomDate.fromInput("nach 1.5.1900"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("1900-1910"))
        assertEquals("BET 1 JAN 1900 AND 1910", GedcomDate.fromInput("zwischen 1.1.1900 und 1910"))
    }

    @Test
    fun englishInput() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 March 1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("March 12, 1890"))
        assertEquals("MAY 1901", GedcomDate.fromInput("May 1901"))
        assertEquals("ABT 1850", GedcomDate.fromInput("about 1850"))
        assertEquals("BEF 12 MAR 1890", GedcomDate.fromInput("before 12 March 1890"))
        assertEquals("FROM 1950", GedcomDate.fromInput("since 1950"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("between 1900 and 1910"))
        // Schraegstriche bleiben unangetastet: 3/12/1890 ist in den USA der 12. Maerz, anderswo der 3. Dezember.
        assertEquals("3/12/1890", GedcomDate.fromInput("3/12/1890"))
    }

    @Test
    fun gedcomStaysGedcom() {
        assertEquals("ABT 1850", GedcomDate.fromInput("abt 1850"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 mar 1890"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("BET 1900 AND 1910"))
        assertEquals("", GedcomDate.fromInput("  "))
    }
}
