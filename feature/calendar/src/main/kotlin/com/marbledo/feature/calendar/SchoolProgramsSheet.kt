package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchoolProgramsSheet(
    catalog: OccasionCatalog,
    languageTag: String,
    onPlan: (OccasionPlanRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.school_programs_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.school_programs_caveat), style = MaterialTheme.typography.bodyMedium)
            }
            items(catalog.schoolPrograms, key = { it.id }) { program ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(program.title(languageTag), style = MaterialTheme.typography.titleSmall)
                        Text(program.note(languageTag), style = MaterialTheme.typography.bodySmall)
                        catalog.sources.filter { it.id in program.sourceIds }.forEach { ReferenceLink(it, languageTag) }
                        TextButton(onClick = {
                            onPlan(OccasionPlanRequest(
                                title = program.title(languageTag), dateIso = null, asCountdown = false,
                                checklist = OccasionTemplates.checklist(PreparationTemplate.SCHOOL, languageTag),
                                description = program.note(languageTag),
                            ))
                        }) { Text(stringResource(R.string.school_program_draft)) }
                    }
                }
            }
        }
    }
}
