package com.marbledo.feature.calendar

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.util.TextNormalizer

/** One research reference with a tappable link, shared by the plan sheet and school programs. */
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
            "community-rules (MIT)" -> R.string.catalog_kind_community_rules
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
