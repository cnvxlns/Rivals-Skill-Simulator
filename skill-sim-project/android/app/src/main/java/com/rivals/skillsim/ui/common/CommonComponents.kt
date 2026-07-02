package com.rivals.skillsim.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rivals.skillsim.i18n.AppStrings
import com.rivals.skillsim.i18n.LanguageCode

@Composable
fun <T> DropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("$label: ${optionLabel(selected)}")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(optionLabel(option)) },
                onClick = {
                    expanded = false
                    onSelected(option)
                },
            )
        }
    }
}

@Composable
fun AppTabs(
    selectedTab: AppTab,
    languageCode: LanguageCode,
    onSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTab.entries.forEach { tab ->
            val labelKey = if (tab == AppTab.Simulator) "tab_simulator" else "tab_calculator"
            val buttonModifier = Modifier.weight(1f)
            if (tab == selectedTab) {
                Button(onClick = { onSelected(tab) }, modifier = buttonModifier) {
                    Text(AppStrings.t(languageCode, labelKey))
                }
            } else {
                OutlinedButton(onClick = { onSelected(tab) }, modifier = buttonModifier) {
                    Text(AppStrings.t(languageCode, labelKey))
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    languageCode: LanguageCode,
    onSelected: (LanguageCode) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownField(
        label = AppStrings.t(languageCode, "language"),
        selected = languageCode,
        options = LanguageCode.entries,
        optionLabel = { it.name },
        onSelected = onSelected,
        modifier = modifier,
    )
}

enum class AppTab {
    Simulator,
    Calculator,
}

fun cardTypeLabel(value: String): String =
    value.split("_").joinToString(" ") { token ->
        token.lowercase().replaceFirstChar { it.uppercase() }
    }
