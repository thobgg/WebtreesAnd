package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.MediaJson

/** Bereich "Fotos": alle Medien des Baums, neueste zuerst, mit den verknuepften Personen. Braucht Modul ab API-Stufe 2. */
@Composable
fun PhotosSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)
        if (state.loadingMedia) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            !viewModel.photosSupported ->
                Text(stringResource(R.string.photos_needs_update), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isEmpty() && state.mediaLoaded && !state.loadingMedia ->
                Text(stringResource(R.string.photos_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isNotEmpty() ->
                MediaGrid(state.media, openWeb, showPeople = true, onEnd = viewModel::loadMoreMedia)
        }
    }
}

/**
 * Raster quadratischer Vorschaubilder - fuer die Fotouebersicht und den Reiter "Medien" im Profil.
 * Ein Tipp oeffnet die Medienseite von webtrees (Bearbeiten und Vollbild gibt es dort).
 *
 * @param onEnd wird gerufen, sobald das letzte Bild sichtbar ist - zum Nachladen der naechsten Seite
 */
@Composable
fun MediaGrid(media: List<MediaJson>, openWeb: (String) -> Unit, showPeople: Boolean = false, onEnd: (() -> Unit)? = null) {
    if (media.isEmpty()) {
        Text(stringResource(R.string.media_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media) { item ->
            if (onEnd != null && item === media.last()) LaunchedEffect(media.size) { onEnd() }

            Column(Modifier.clickable { openWeb(item.url) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
                    if (item.thumb != null) {
                        AsyncImage(model = item.thumb, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(
                    item.title.ifEmpty { item.mime }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
                if (showPeople && item.people.isNotEmpty()) {
                    Text(
                        item.people.joinToString(", ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}
