package de.bgghome.webtrees.nativ.ui.tree

import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person

/** Ein "+"-Kaestchen: legt relation (father|mother|spouse|child) zu relativeTo an. */
data class Placeholder(val relation: String, val relativeTo: Person, val families: List<Pair<String, String?>> = emptyList())

/** Ein Kaestchen im Baum - Person oder Platzhalter. Koordinaten in dp, linke obere Ecke. */
data class TreeBox(
    val person: Person? = null,
    val placeholder: Placeholder? = null,
    val x: Float,
    val y: Float,
    val isFocus: Boolean = false,
    /** Kekule-Nummer (nur Ahnen): 1 = Mittelperson, 2 = Vater, 3 = Mutter ... */
    val ahnen: Int? = null,
    /** Diese Person hat Eltern, die noch nicht geladen sind -> "weiter nach oben"-Symbol ueber der Karte. */
    val canExpand: Boolean = false,
) {
    val centerX get() = x + TreeLayout.BOX_W / 2
    val centerY get() = y + TreeLayout.BOX_H / 2
    val bottom get() = y + TreeLayout.BOX_H

    fun contains(px: Float, py: Float) = px >= x && px <= x + TreeLayout.BOX_W && py >= y && py <= y + TreeLayout.BOX_H
}

/** Ein Linienzug (Eckpunkte in dp). Gezeichnet wird er mit runden Ecken - wie beim Vorbild MyHeritage. */
data class Connector(val points: List<Pair<Float, Float>>)

/**
 * Sanduhr-Baum: Mittelperson, Ahnen nach oben (binaer, Kekule-Nummern), Partner daneben,
 * Nachkommen nach unten. Reine Rechnung ohne Android/Compose - siehe TreeLayoutTest.
 */
class TreeLayout private constructor(
    val boxes: List<TreeBox>,
    val connectors: List<Connector>,
    val width: Float,
    val height: Float,
    val canEdit: Boolean,
) {

    val focus: TreeBox get() = boxes.first { it.isFocus }

    fun boxAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { it.contains(x, y) }

    /** Das Aufklapp-Symbol sitzt mittig ueber der Karte. */
    fun expandAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { box ->
        box.canExpand && (x - box.centerX) * (x - box.centerX) + (y - (box.y - EXPAND_OFFSET)) * (y - (box.y - EXPAND_OFFSET)) <= PLUS_HIT_RADIUS * PLUS_HIT_RADIUS
    }

    /** Hat diese Karte eine "+"-Lasche (Verwandte hinzufuegen)? */
    fun hasPlus(box: TreeBox): Boolean = canEdit && box.person != null && !box.person.isPrivate

    /** Die "+"-Lasche haengt mittig an der Unterkante der Karte; sie wird vor der Karte selbst geprueft. */
    fun plusAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { box ->
        hasPlus(box) && (x - box.centerX) * (x - box.centerX) + (y - box.bottom) * (y - box.bottom) <= PLUS_HIT_RADIUS * PLUS_HIT_RADIUS
    }

    companion object {
        // Kompakte Querkarten wie in der MyHeritage-App auf Android: rundes Foto links, Name, Jahre.
        const val BOX_W = 172f
        const val BOX_H = 60f
        const val H_GAP = 18f
        const val SPOUSE_GAP = 22f
        const val V_GAP = 64f
        const val PADDING = 56f
        const val PLUS_RADIUS = 12f
        const val PLUS_HIT_RADIUS = 20f
        /** Abstand des Aufklapp-Symbols (Mitte) ueber der Kartenoberkante */
        const val EXPAND_OFFSET = 20f

        /** Fehlende Eltern werden nur bis zu dieser Generation als "+" angeboten, sonst wird die oberste Reihe zu voll. */
        private const val PLACEHOLDER_MAX_GEN = 3

        fun build(pedigree: Pedigree, descendants: DescendantNode, canEdit: Boolean): TreeLayout {
            val builder = Builder(pedigree, canEdit)

            builder.placeDescendants(descendants, 0f, 0, isFocus = true)
            builder.placeAncestors()

            return builder.finish()
        }
    }

    private class Builder(pedigree: Pedigree, val canEdit: Boolean) {
        val boxes = mutableListOf<TreeBox>()
        val connectors = mutableListOf<Connector>()
        val ancestors = pedigree.ancestors.associate { it.n to it.person }
        val hasParents = pedigree.ancestors.filter { it.hasParents }.map { it.n }.toSet()
        val generations = pedigree.generations
        lateinit var focusBox: TreeBox

        // ── Nachkommen (nach unten) ──────────────────────────────────

        private fun spouseCount(node: DescendantNode, isFocus: Boolean) =
            node.families.count { it.spouse != null } + if (isFocus && canEdit) 1 else 0

        private fun unitWidth(node: DescendantNode, isFocus: Boolean) = BOX_W + spouseCount(node, isFocus) * (SPOUSE_GAP + BOX_W)

        private fun childSlots(node: DescendantNode, isFocus: Boolean): Int =
            node.families.sumOf { it.children.size } + if (isFocus && canEdit) 1 else 0

        private fun subtreeWidth(node: DescendantNode, isFocus: Boolean): Float {
            var children = node.families.sumOf { family -> family.children.sumOf { subtreeWidth(it, false).toDouble() } }.toFloat()
            if (isFocus && canEdit) children += BOX_W
            val slots = childSlots(node, isFocus)
            if (slots > 1) children += (slots - 1) * H_GAP

            return maxOf(unitWidth(node, isFocus), children)
        }

        fun placeDescendants(node: DescendantNode, left: Float, depth: Int, isFocus: Boolean): TreeBox {
            val span = subtreeWidth(node, isFocus)
            val y = depth * (BOX_H + V_GAP)
            var x = left + (span - unitWidth(node, isFocus)) / 2

            val personBox = TreeBox(person = node.person, x = x, y = y, isFocus = isFocus).also { boxes += it }
            if (isFocus) focusBox = personBox
            x += BOX_W

            // Partner rechts daneben; von jeder Verbindung gehen die gemeinsamen Kinder ab.
            val anchors = mutableListOf<Pair<Float, Float>>()
            node.families.forEach { family ->
                if (family.spouse != null) {
                    val spouseBox = TreeBox(person = family.spouse, x = x + SPOUSE_GAP, y = y).also { boxes += it }
                    connectors += Connector(listOf(personBox.x + BOX_W to personBox.centerY, spouseBox.x to spouseBox.centerY))
                    anchors += (spouseBox.x - SPOUSE_GAP / 2) to spouseBox.centerY
                    x += SPOUSE_GAP + BOX_W
                } else {
                    anchors += personBox.centerX to personBox.bottom
                }
            }

            if (isFocus && canEdit) {
                val spouseAdd = TreeBox(placeholder = Placeholder("spouse", node.person), x = x + SPOUSE_GAP, y = y).also { boxes += it }
                connectors += Connector(listOf(personBox.x + BOX_W to personBox.centerY, spouseAdd.x to spouseAdd.centerY))
            }

            // Kinderreihe, mittig unter der Einheit
            var childrenWidth = node.families.sumOf { family -> family.children.sumOf { subtreeWidth(it, false).toDouble() } }.toFloat()
            val slots = childSlots(node, isFocus)
            if (isFocus && canEdit) childrenWidth += BOX_W
            if (slots > 1) childrenWidth += (slots - 1) * H_GAP

            var childLeft = left + (span - childrenWidth) / 2
            val railY = y + BOX_H + V_GAP / 2

            node.families.forEachIndexed { index, family ->
                val (anchorX, anchorY) = anchors[index]
                val childBoxes = family.children.map { child ->
                    placeDescendants(child, childLeft, depth + 1, false).also { childLeft += subtreeWidth(child, false) + H_GAP }
                }
                connectDown(anchorX, anchorY, railY, childBoxes)
            }

            if (isFocus && canEdit) {
                val families = node.families.map { it.xref to it.spouse?.name }
                val childAdd = TreeBox(placeholder = Placeholder("child", node.person, families), x = childLeft, y = y + BOX_H + V_GAP).also { boxes += it }
                val (anchorX, anchorY) = anchors.lastOrNull() ?: (personBox.centerX to personBox.bottom)
                connectDown(anchorX, anchorY, railY, listOf(childAdd))
            }

            return personBox
        }

        private fun connectDown(anchorX: Float, anchorY: Float, railY: Float, children: List<TreeBox>) {
            if (children.isEmpty()) return

            // Je Kind ein eigener Linienzug: Anker -> Schiene -> Kind. Auf der Schiene liegen sie uebereinander.
            children.forEach { child ->
                connectors += Connector(listOf(anchorX to anchorY, anchorX to railY, child.centerX to railY, child.centerX to child.y))
            }
        }

        // ── Ahnen (nach oben) ────────────────────────────────────────

        private fun generationOf(n: Int) = 31 - Integer.numberOfLeadingZeros(n)

        /** Steht an Kekule-Platz n etwas - eine Person oder ein "+"? */
        private fun slot(n: Int): Boolean {
            if (ancestors.containsKey(n)) return true
            val child = ancestors[n / 2] ?: return false

            // Hat das Kind Eltern, die nur noch nicht geladen sind, gibt es nichts hinzuzufuegen.
            if (n / 2 in hasParents && 2 * (n / 2) !in ancestors && 2 * (n / 2) + 1 !in ancestors) return false

            return canEdit && !child.isPrivate && generationOf(n) <= PLACEHOLDER_MAX_GEN
        }

        private fun parentsOf(n: Int): List<Int> =
            if (ancestors.containsKey(n)) listOf(2 * n, 2 * n + 1).filter(::slot) else emptyList()

        private fun ancestorWidth(n: Int): Float {
            val parents = parentsOf(n)
            if (parents.isEmpty()) return BOX_W

            return maxOf(BOX_W, parents.map(::ancestorWidth).sum() + (parents.size - 1) * H_GAP)
        }

        /** Legt Platz n und alles darueber an; gibt die Mitte (x) des Kaestchens zurueck. Platz 1 wird nicht gezeichnet. */
        private fun placeAncestor(n: Int, left: Float, out: MutableList<TreeBox>, lines: MutableList<Connector>): Float {
            val span = ancestorWidth(n)
            val parents = parentsOf(n)
            val y = -generationOf(n) * (BOX_H + V_GAP)

            var parentLeft = left + (span - (parents.map(::ancestorWidth).sum() + (parents.size - 1).coerceAtLeast(0) * H_GAP)) / 2
            val parentCenters = parents.map { parent ->
                placeAncestor(parent, parentLeft, out, lines).also { parentLeft += ancestorWidth(parent) + H_GAP }
            }

            val center = if (parentCenters.isEmpty()) left + span / 2 else (parentCenters.first() + parentCenters.last()) / 2

            if (n > 1) {
                val person = ancestors[n]
                out += if (person != null) {
                    // Eltern vorhanden, aber nicht geladen (oberste Reihe): Aufklapp-Symbol
                    TreeBox(person = person, x = center - BOX_W / 2, y = y, ahnen = n, canExpand = n in hasParents && 2 * n !in ancestors && 2 * n + 1 !in ancestors)
                } else {
                    TreeBox(placeholder = Placeholder(if (n % 2 == 0) "father" else "mother", ancestors.getValue(n / 2)), x = center - BOX_W / 2, y = y)
                }
            }

            if (parentCenters.isNotEmpty()) {
                val railY = y - V_GAP / 2
                parentCenters.forEach { lines += Connector(listOf(center to y, center to railY, it to railY, it to y - V_GAP)) }
            }

            return center
        }

        fun placeAncestors() {
            val out = mutableListOf<TreeBox>()
            val lines = mutableListOf<Connector>()
            val rootCenter = placeAncestor(1, 0f, out, lines)

            // Der Ahnenbaum wurde fuer sich gerechnet - so verschieben, dass sein Fuss auf der Mittelperson steht.
            val shift = focusBox.centerX - rootCenter
            out.forEach { boxes += it.copy(x = it.x + shift) }
            lines.forEach { line -> connectors += Connector(line.points.map { (x, y) -> x + shift to y }) }
        }

        fun finish(): TreeLayout {
            val minX = boxes.minOf { it.x } - PADDING
            val minY = boxes.minOf { it.y } - PADDING
            val maxX = boxes.maxOf { it.x + BOX_W } + PADDING
            val maxY = boxes.maxOf { it.y + BOX_H } + PADDING

            return TreeLayout(
                boxes.map { it.copy(x = it.x - minX, y = it.y - minY) },
                connectors.map { line -> Connector(line.points.map { (x, y) -> x - minX to y - minY }) },
                maxX - minX, maxY - minY, canEdit,
            )
        }
    }
}
