package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.Anniversary
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.Placeholder
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout

// ── Gemeinsames ──────────────────────────────────────────────────────

/** Kopfzeile: Baumname mit Wechsel-Pfeil und Personenzahl - wie beim Vorbild. */
@Composable
private fun TreeTitleBar(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, trailing: @Composable () -> Unit = {}) {
    val tree = state.tree
    val canSwitch = (state.info?.trees?.size ?: 0) > 1

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).then(if (canSwitch) Modifier.clickable { viewModel.showTreePicker() } else Modifier)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tree?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (canSwitch) Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.menu_switch_tree))
                }
                if ((tree?.individuals ?: 0) > 0) {
                    Text(stringResource(R.string.tree_people_count, tree!!.individuals), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
            MainMenu(state, viewModel, openWeb)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun PersonRow(person: Person, selected: Boolean = false, label: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        leadingContent = { Avatar(person, 44.dp) },
        trailingContent = trailing,
        overlineContent = label?.let { { Text(it) } },
        headlineContent = { Text(if (person.isPrivate) stringResource(R.string.person_private) else person.name.ifEmpty { stringResource(R.string.person_no_name) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val place = person.birth?.place?.short.orEmpty()
            Text(listOf(person.lifespan, place).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        modifier = if (onClick != null && !person.isPrivate) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

// ── Baum ─────────────────────────────────────────────────────────────

@Composable
fun TreeSection(state: UiState, viewModel: AppViewModel, wide: Boolean, openWeb: (String) -> Unit, onPlaceholder: (Placeholder) -> Unit) {
    val detail = state.detail

    // Handy: das Profil ist eine eigene Seite.
    if (!wide && state.profileOpen && detail != null) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    Text(stringResource(R.string.action_profile), style = MaterialTheme.typography.titleMedium)
                }
            }
            ProfilePanel(state, detail, viewModel, openWeb, onClose = null)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!state.treeFullscreen) {
            TreeTitleBar(state, viewModel, openWeb) { GenerationsChip(state, viewModel) }

            // "Person finden ..." - fuehrt in die Suche
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.setSection(Section.Search) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.tree_find), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(Modifier.fillMaxSize()) {
            // Tablet: Profil dauerhaft links neben dem Baum
            if (wide && !state.treeFullscreen && state.selected != null) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    if (detail != null) ProfilePanel(state, detail, viewModel, openWeb, onClose = viewModel::closePanel)
                    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                TreeCanvas(state, viewModel, wide, onPlaceholder)

                // Handy: kompakte Kurzkarte am unteren Rand statt eines Panels
                if (!wide && !state.treeFullscreen && state.selected != null) {
                    QuickCard(state, viewModel, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun TreeCanvas(state: UiState, viewModel: AppViewModel, wide: Boolean, onPlaceholder: (Placeholder) -> Unit) {
    val pedigree = state.pedigree
    val descendants = state.descendants
    val canEdit = state.tree?.canEdit == true

    // Nach jedem Wechsel der Mittelperson und nach jedem Schreiben sind die Daten geleert -> neu laden.
    LaunchedEffect(state.root, pedigree == null || descendants == null) {
        if (state.root != null && (pedigree == null || descendants == null)) viewModel.loadChart()
    }

    val layout = remember(pedigree, descendants, canEdit) {
        if (pedigree != null && descendants != null) TreeLayout.build(pedigree, descendants.tree, canEdit) else null
    }

    FamilyTreeView(
        layout = layout,
        selected = state.selected,
        fullscreen = state.treeFullscreen,
        initialScale = if (wide) 1f else 0.8f,
        compact = !wide,
        onToggleFullscreen = { viewModel.setTreeFullscreen(!state.treeFullscreen) },
        onPerson = { viewModel.select(it.xref) },
        onPlus = { viewModel.requestAddRelative(it.xref) },
        onPlaceholder = onPlaceholder,
        onExpand = viewModel::expandAncestors,
    )
}

@Composable
private fun GenerationsChip(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.tree_generations, state.ancestorGenerations))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (2..6).forEach { n ->
                DropdownMenuItem(text = { Text(stringResource(R.string.tree_generations, n)) }, onClick = { open = false; viewModel.setAncestorGenerations(n) })
            }
        }
    }
}

/** Handy: wer ist angetippt - mit den zwei wichtigsten Wegen (Profil, als Mittelperson). */
@Composable
private fun QuickCard(state: UiState, viewModel: AppViewModel, modifier: Modifier) {
    val person = state.detail?.person?.takeIf { it.xref == state.selected }

    Surface(
        modifier = modifier.padding(start = 12.dp, end = 72.dp, bottom = 12.dp).widthIn(max = 520.dp).fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (person == null) {
                    CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.weight(1f))
                } else {
                    Avatar(person, 44.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val relation = state.detail?.relationship.orEmpty().replaceFirstChar { it.uppercase() }
                        Text(listOf(relation, person.lifespan).filter { it.isNotBlank() }.joinToString(" | "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconButton(onClick = viewModel::closePanel) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            if (person != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = viewModel::openProfile) { Text(stringResource(R.string.action_profile)) }
                    if (state.root != person.xref) {
                        OutlinedButton(onClick = { viewModel.setRoot(person.xref) }) { Text(stringResource(R.string.action_make_root)) }
                    }
                }
            }
        }
    }
}

// ── Suche ────────────────────────────────────────────────────────────

@Composable
fun SearchSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)

        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.widthIn(max = 720.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    placeholder = { Text(stringResource(R.string.tree_find)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.search("") }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear)) }
                        }
                    },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )

                if (state.people.isEmpty() && !state.loadingPeople) {
                    Text(
                        stringResource(if (state.query.isEmpty()) R.string.list_empty else R.string.list_nothing_found),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.people, key = { _, person -> person.xref }) { index, person ->
                        // Kurz vor dem Ende die naechste Seite holen
                        if (index >= state.people.size - 10) LaunchedEffect(state.nextPage) { viewModel.loadMore() }

                        // Ein Treffer wird zur Mittelperson des Baums
                        PersonRow(person, selected = person.xref == state.root, onClick = { viewModel.setRoot(person.xref) })
                    }
                    if (state.loadingPeople) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    }
                }
            }
        }
    }
}

// ── Start ────────────────────────────────────────────────────────────

@Composable
fun HomeSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val user = state.info?.user
    val tree = state.tree

    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)

        // Am Tablet nicht ueber die ganze Breite ziehen
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    if (user?.loggedIn == true) stringResource(R.string.home_greeting, user.realName.ifEmpty { user.userName }) else stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                HomeCard {
                    Text(tree?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val facts = listOfNotNull(
                        tree?.individuals?.takeIf { it > 0 }?.let { stringResource(R.string.tree_people_count, it) },
                        tree?.let { stringResource(R.string.home_role, roleLabel(it.role)) },
                    )
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.setSection(Section.Tree) }) { Text(stringResource(R.string.home_open_tree)) }
                        OutlinedButton(onClick = { viewModel.setSection(Section.Search) }) { Text(stringResource(R.string.nav_search)) }
                    }
                }
            }
            state.recent.firstOrNull { it.xref == state.home }?.let { home ->
                item {
                    HomeCard(padding = false) {
                        PersonRow(home, label = stringResource(R.string.home_start_person), onClick = { viewModel.setRoot(home.xref) })
                    }
                }
            }
            if (viewModel.anniversariesSupported) {
                item { Text(stringResource(R.string.home_anniversaries).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    HomeCard(padding = state.anniversaries.isEmpty()) {
                        if (state.anniversaries.isEmpty()) {
                            Text(stringResource(R.string.anniv_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.anniversaries.take(8).forEachIndexed { index, anniversary ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AnniversaryRow(anniversary, onOpen = viewModel::setRoot)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.home_recent).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            if (state.recent.isEmpty()) {
                item { Text(stringResource(R.string.home_recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    HomeCard(padding = false) {
                        state.recent.forEachIndexed { index, person ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PersonRow(person, onClick = { viewModel.setRoot(person.xref) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnniversaryRow(anniversary: Anniversary, onOpen: (String) -> Unit) {
    val whoToOpen = anniversary.person ?: anniversary.couple.firstOrNull()
    val whenText = when (anniversary.inDays) {
        0 -> stringResource(R.string.anniv_today)
        1 -> stringResource(R.string.anniv_tomorrow)
        else -> stringResource(R.string.anniv_in_days, anniversary.inDays)
    }

    ListItem(
        leadingContent = whoToOpen?.let { { Avatar(it, 44.dp) } },
        overlineContent = { Text(whenText, color = if (anniversary.inDays == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(anniversary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(R.string.anniv_line, anniversary.label, anniversary.years)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (whoToOpen != null && !whoToOpen.isPrivate) Modifier.clickable { onOpen(whoToOpen.xref) } else Modifier,
    )
}

@Composable
private fun HomeCard(padding: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Column(if (padding) Modifier.padding(16.dp) else Modifier) { content() }
    }
}

// ── Fotos ────────────────────────────────────────────────────────────

@Composable
fun PhotosSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)
        if (state.loadingMedia) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            !viewModel.photosSupported -> Text(stringResource(R.string.photos_needs_update), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isEmpty() && state.mediaLoaded && !state.loadingMedia -> Text(stringResource(R.string.photos_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isNotEmpty() -> MediaGrid(state.media, openWeb, showPeople = true, onEnd = viewModel::loadMoreMedia)
        }
    }
}
