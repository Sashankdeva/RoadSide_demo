package com.roadside.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadside.agent.VehicleContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    context: VehicleContext,
    onVehicleTypeChanged: (String) -> Unit,
    onProblemDescriptionChanged: (String) -> Unit,
    onAskRoadSide: (String) -> Unit,
    onListenClicked: () -> Unit,
    onInspectClicked: () -> Unit,
    onVoiceInputRequested: () -> Unit,
    onDebugClicked: () -> Unit = {}
) {
    var problemText by remember(context.userProblemDescription) {
        mutableStateOf(context.userProblemDescription)
    }
    var expandedVehicleDropdown by remember { mutableStateOf(false) }
    val vehicles = listOf("Motorcycle", "Scooter", "Car (RoadSide Pro)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ROADSide",
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 28.sp
        )

        Text(
            text = "Offline Vehicle Acoustic & Visual Diagnostics",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Vehicle Selector
        Text(text = "Vehicle", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(
            expanded = expandedVehicleDropdown,
            onExpandedChange = { expandedVehicleDropdown = !expandedVehicleDropdown }
        ) {
            OutlinedTextField(
                value = context.vehicleType,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVehicleDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("Selected Vehicle") }
            )
            ExposedDropdownMenu(
                expanded = expandedVehicleDropdown,
                onDismissRequest = { expandedVehicleDropdown = false }
            ) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = { Text(vehicle) },
                        onClick = {
                            onVehicleTypeChanged(vehicle)
                            expandedVehicleDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Problem Description
        Text(text = "Describe your problem", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = problemText,
            onValueChange = {
                problemText = it
                onProblemDescriptionChanged(it)
            },
            placeholder = { Text("e.g. My bike is making a strange noise...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            trailingIcon = {
                IconButton(onClick = onVoiceInputRequested) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        Button(
            onClick = {
                if (problemText.isNotBlank()) {
                    onAskRoadSide(problemText)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ask RoadSide")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Direct Diagnostics", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onListenClicked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Listen")
            }

            Button(
                onClick = onInspectClicked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Inspect")
            }
        }

        // Quick status card if evidence already exists
        if (context.audioEvidence != null || context.visionEvidence != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Active Session Evidence:", style = MaterialTheme.typography.labelLarge)
                    if (context.audioEvidence != null) {
                        Text(text = "• Audio: ${context.audioEvidenceLabel.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (context.visionEvidence != null) {
                        Text(text = "• Visual: ${context.visionEvidenceLabel.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Developer debug entry point (small, non-intrusive)
        TextButton(
            onClick = onDebugClicked,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = "YAMNet Debug", style = MaterialTheme.typography.labelSmall)
        }
    }
}
