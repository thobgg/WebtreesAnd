package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.Ancestor
import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.tree.Sibling
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeLayoutTest {

    private fun p(xref: String, sex: String = "M") = Person(xref = xref, name = xref, sex = sex)

    /** Mittelperson mit zwei Ehen, Kindern, Enkeln; Ahnen lueckenhaft (Mutter fehlt, Grossvater ohne Eltern). */
    private fun sample(canEdit: Boolean, siblings: Map<String, List<Sibling>> = emptyMap(), cousins: Boolean = false): TreeLayout {
        val pedigree = Pedigree(
            root = "I1", generations = 4,
            ancestors = listOf(
                Ancestor(1, p("I1")), Ancestor(2, p("I2")), Ancestor(4, p("I4")), Ancestor(5, p("I5", "F")),
                Ancestor(8, p("I8")), Ancestor(9, p("I9", "F")), Ancestor(11, p("I11", "F")),
            ),
        )
        val grandchildren = listOf(DescendantNode(p("G1")), DescendantNode(p("G2")), DescendantNode(p("G3")))
        val descendants = DescendantNode(
            p("I1"),
            listOf(
                DescendantFamily("F1", spouse = p("S1", "F"), children = listOf(
                    DescendantNode(p("C1"), listOf(DescendantFamily("F3", spouse = p("S3", "F"), children = grandchildren))),
                    DescendantNode(p("C2", "F")),
                )),
                DescendantFamily("F2", spouse = p("S2", "F"), children = listOf(DescendantNode(p("C3")))),
            ),
        )

        return TreeLayout.build(pedigree, descendants, canEdit, siblings, cousins)
    }

    private fun assertNoOverlap(layout: TreeLayout) {
        val boxes = layout.boxes
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]; val b = boxes[j]
            val apart = a.x + TreeLayout.BOX_W <= b.x || b.x + TreeLayout.BOX_W <= a.x ||
                a.y + TreeLayout.BOX_H <= b.y || b.y + TreeLayout.BOX_H <= a.y
            assertTrue("Ueberlappung: ${a.person?.xref ?: a.placeholder} / ${b.person?.xref ?: b.placeholder}", apart)
        }
    }

    @Test
    fun readOnlyTreeHasEveryPersonOnceAndNoPlaceholders() {
        val layout = sample(canEdit = false)

        assertNoOverlap(layout)
        assertTrue(layout.boxes.none { it.placeholder != null })
        // 7 Ahnen (inkl. Mittelperson) + 3 Partner + 3 Kinder + 3 Enkel
        assertEquals(16, layout.boxes.size)
        assertEquals(1, layout.boxes.count { it.isFocus })
        assertEquals(layout.boxes.size, layout.boxes.map { it.person!!.xref }.toSet().size)
    }

    @Test
    fun editableTreeOffersMissingRelatives() {
        val layout = sample(canEdit = true)

        assertNoOverlap(layout)

        val placeholders = layout.boxes.mapNotNull { it.placeholder }
        // Mutter der Mittelperson fehlt
        assertNotNull(placeholders.firstOrNull { it.relation == "mother" && it.relativeTo.xref == "I1" })
        // I5 (Generation 2) hat nur die Mutter I11 -> Vater wird angeboten
        assertNotNull(placeholders.firstOrNull { it.relation == "father" && it.relativeTo.xref == "I5" })
        // Generation 3 (I8, I9, I11): deren Eltern laegen in Generation 4 - dort keine Platzhalter mehr
        assertNull(placeholders.firstOrNull { it.relativeTo.xref == "I8" })
        // Partner und Kind nur fuer die Mittelperson, das Kind mit beiden Verbindungen zur Wahl
        assertEquals(1, placeholders.count { it.relation == "spouse" })
        assertEquals(listOf("F1", "F2"), placeholders.single { it.relation == "child" }.families.map { it.first })
    }

    @Test
    fun generationsAreRowsAndFocusSitsBetweenParentsAndChildren() {
        val layout = sample(canEdit = false)
        val y = layout.boxes.associate { it.person!!.xref to it.y }

        assertTrue(y.getValue("I8") < y.getValue("I4"))
        assertTrue(y.getValue("I4") < y.getValue("I2"))
        assertTrue(y.getValue("I2") < y.getValue("I1"))
        assertEquals(y.getValue("I1"), y.getValue("S1"), 0.01f)
        assertTrue(y.getValue("I1") < y.getValue("C1"))
        assertTrue(y.getValue("C1") < y.getValue("G1"))
        assertEquals(y.getValue("I8"), y.getValue("I11"), 0.01f)
    }

    @Test
    fun siblingsSitBesideTheirPersonOnTheSameRow() {
        // Zwei Geschwister der Mittelperson (eines verheiratet), ein Bruder des Vaters, eine Schwester der Grossmutter I5
        val layout = sample(
            canEdit = true,
            siblings = mapOf(
                "I1" to listOf(Sibling(p("B1")), Sibling(p("B2", "F"), listOf(p("B2S")))),
                "I2" to listOf(Sibling(p("U1"))),
                "I5" to listOf(Sibling(p("A1", "F"), listOf(p("A1S")))),
            ),
        )
        assertNoOverlap(layout)

        val box = layout.boxes.filter { it.person != null }.associateBy { it.person!!.xref }
        // Mittelperson und Vater: Geschwister links; Mutter-Seite (I5): rechts
        assertEquals(box.getValue("I1").y, box.getValue("B1").y, 0.01f)
        assertTrue(box.getValue("B2").x < box.getValue("B1").x || box.getValue("B1").x < box.getValue("I1").x)
        assertTrue(box.getValue("B2S").x > box.getValue("B2").x && box.getValue("B2S").x < box.getValue("I1").x)
        assertTrue(box.getValue("U1").x < box.getValue("I2").x && box.getValue("U1").y == box.getValue("I2").y)
        assertTrue(box.getValue("A1").x > box.getValue("I5").x && box.getValue("A1S").x > box.getValue("A1").x)
        // Der Ahnenbaum steht weiter mittig ueber der Mittelperson: Vater links, Mutter-Platzhalter rechts
        val mother = layout.boxes.first { it.placeholder?.relation == "mother" && it.placeholder.relativeTo.xref == "I1" }
        assertTrue(box.getValue("I2").centerX < box.getValue("I1").centerX && mother.centerX > box.getValue("I1").centerX)
        assertTrue(layout.boxes.all { it.x >= 0 && it.x + TreeLayout.BOX_W <= layout.width })
    }

    @Test
    fun cousinsHangBelowTheirParentsWithoutTouchingTheFocusRow() {
        // Onkel U1 mit drei Kindern (breiter als seine Karte), Tante A1 (Schwester der fehlenden Mutter gibt es nicht ->
        // Cousins nur vaeterlicherseits); die Mittelperson hat selbst zwei Geschwister in derselben Reihe.
        val siblings = mapOf(
            "I1" to listOf(Sibling(p("B1")), Sibling(p("B2", "F"), listOf(p("B2S")))),
            "I2" to listOf(Sibling(p("U1"), listOf(p("U1S", "F")), listOf(p("K1"), p("K2"), p("K3")))),
        )
        val without = sample(canEdit = true, siblings = siblings, cousins = false)
        val with = sample(canEdit = true, siblings = siblings, cousins = true)
        assertNoOverlap(without)
        assertNoOverlap(with)

        assertNull(without.boxes.firstOrNull { it.person?.xref == "K1" })

        val box = with.boxes.filter { it.person != null }.associateBy { it.person!!.xref }
        // Cousins in der Reihe der Mittelperson, unter dem Onkel, links von deren Geschwistern
        listOf("K1", "K2", "K3").forEach { assertEquals(box.getValue("I1").y, box.getValue(it).y, 0.01f) }
        assertTrue(box.getValue("K3").x + TreeLayout.BOX_W <= box.getValue("B1").x)
        assertTrue(box.getValue("K1").x < box.getValue("K2").x && box.getValue("K2").x < box.getValue("K3").x)
        assertTrue(box.getValue("K1").y > box.getValue("U1").y)
        assertTrue(with.boxes.all { it.x >= 0 && it.x + TreeLayout.BOX_W <= with.width })
    }

    @Test
    fun tapHitsTheRightBox() {
        val layout = sample(canEdit = false)
        val box = layout.boxes.first { it.person?.xref == "C3" }

        assertEquals("C3", layout.boxAt(box.centerX, box.centerY)?.person?.xref)
        assertNull(layout.boxAt(-5f, -5f))
        assertTrue(layout.width > 0 && layout.height > 0)
        assertTrue(layout.boxes.all { it.x >= 0 && it.y >= 0 && it.x + TreeLayout.BOX_W <= layout.width })
    }
}
