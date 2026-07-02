package com.rivals.skillsim

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rivals.skillsim.data.local.UserPrefsDataStore
import com.rivals.skillsim.data.local.UserPrefsSnapshot
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import com.rivals.skillsim.i18n.AppStrings
import com.rivals.skillsim.i18n.LanguageCode
import com.rivals.skillsim.ui.calculator.CalculatorScreen
import com.rivals.skillsim.ui.calculator.CalculatorViewModel
import com.rivals.skillsim.ui.common.AppTab
import com.rivals.skillsim.ui.common.SegmentedTabs
import com.rivals.skillsim.ui.simulator.SimulatorScreen
import com.rivals.skillsim.ui.simulator.SimulatorViewModel
import com.rivals.skillsim.ui.theme.RivalsSkillSimTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RivalsSkillSimApp(
    repository: SkillRepositoryContract,
    userPrefsDataStore: UserPrefsDataStore,
) {
    val prefs by userPrefsDataStore.preferences.collectAsStateWithLifecycle(initialValue = UserPrefsSnapshot())
    val languageCode = LanguageCode.from(prefs.languageCode)
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Simulator) }
    val simulatorViewModel: SimulatorViewModel = viewModel(
        factory = viewModelFactory { SimulatorViewModel(repository) },
    )
    val calculatorViewModel: CalculatorViewModel = viewModel(
        factory = viewModelFactory { CalculatorViewModel(repository, userPrefsDataStore) },
    )

    RivalsSkillSimTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = AppStrings.t(languageCode, "hdr_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        LanguageMenu(
                            languageCode = languageCode,
                            onSelected = { nextLanguage ->
                                coroutineScope.launch {
                                    userPrefsDataStore.saveLanguage(nextLanguage.name)
                                }
                            },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SegmentedTabs(
                        selectedTab = selectedTab,
                        languageCode = languageCode,
                        onSelected = { selectedTab = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Crossfade(
                        targetState = selectedTab,
                        modifier = Modifier.weight(1f),
                        label = "tabContent",
                    ) { tab ->
                        when (tab) {
                            AppTab.Simulator -> SimulatorScreen(
                                viewModel = simulatorViewModel,
                                languageCode = languageCode,
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppTab.Calculator -> CalculatorScreen(
                                viewModel = calculatorViewModel,
                                languageCode = languageCode,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMenu(
    languageCode: LanguageCode,
    onSelected: (LanguageCode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(
            text = languageCode.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        LanguageCode.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.name) },
                onClick = {
                    expanded = false
                    onSelected(option)
                },
            )
        }
    }
}

private fun <VM : ViewModel> viewModelFactory(creator: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
