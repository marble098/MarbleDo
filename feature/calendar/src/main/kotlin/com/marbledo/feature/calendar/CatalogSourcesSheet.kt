package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.util.TextNormalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSourcesSheet(state: OccasionState, languageTag: String, includeEducation: Boolean = false, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val numeralMode = LocalNumeralMode.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.catalog_sources_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.catalog_delivery_explanation), style = MaterialTheme.typography.bodyMedium)
            Text(TextNormalizer.formatDigits(stringResource(R.string.catalog_version,
                state.catalog.dataVersion.orEmpty(), state.catalog.updatedAt.orEmpty()), numeralMode))
            Text(stringResource(R.string.catalog_coverage_caveat), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.catalog_lunar_caveat), style = MaterialTheme.typography.bodySmall)
            state.lastDeliveryUrl?.let { Text(stringResource(R.string.catalog_last_delivery, it), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.catalog_delivery_paths), style = MaterialTheme.typography.titleSmall)
            OccasionRepository.CATALOG_URLS.forEachIndexed { index, url ->
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(url) }.onFailure {
                        Toast.makeText(context, R.string.catalog_link_failed, Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text(TextNormalizer.formatDigits(stringResource(R.string.catalog_delivery_path, index + 1, java.net.URI(url).host), numeralMode))
                }
            }
            Text(stringResource(R.string.catalog_references), style = MaterialTheme.typography.titleMedium)
            val referencedIds = state.catalog.occasions
                .filter { includeEducation || it.category != OccasionCategory.EDUCATIONAL }
                .flatMap { it.sourceIds }.toMutableSet()
            if (includeEducation) {
                referencedIds += state.catalog.schoolPrograms.flatMap { it.sourceIds }
                referencedIds += state.catalog.yearly.values.flatMap { it.occasions }.flatMap { it.sourceIds }
            }
            state.catalog.sources.filter { it.id in referencedIds }.forEach { reference -> ReferenceLink(reference, languageTag) }
        }
    }
}

@Composable
internal fun ReferenceLink(reference: CatalogReference, languageTag: String) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    Column {
        TextButton(onClick = {
            runCatching { handler.openUri(reference.url) }.onFailure {
                Toast.makeText(context, R.string.catalog_link_failed, Toast.LENGTH_LONG).show()
            }
        }) { Text(reference.title(languageTag)) }
        val kind = when (reference.kind) {
            "curated" -> R.string.catalog_kind_curated
            "traditional" -> R.string.catalog_kind_traditional
            "institution" -> R.string.catalog_kind_institution
            "guidance" -> R.string.catalog_kind_guidance
            "legacy" -> R.string.catalog_kind_legacy
            else -> R.string.catalog_kind_report
        }
        Text(
            TextNormalizer.formatDigits("${stringResource(kind)} · ${stringResource(R.string.catalog_reference_checked, reference.checkedAt)}", LocalNumeralMode.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun evidenceLabelRes(evidence: OccasionEvidence): Int = when (evidence) {
    OccasionEvidence.SOURCED -> R.string.catalog_evidence_sourced
    OccasionEvidence.TRADITIONAL -> R.string.catalog_evidence_traditional
    OccasionEvidence.GUIDANCE -> R.string.catalog_evidence_guidance
    OccasionEvidence.LEGACY -> R.string.catalog_evidence_legacy
}
