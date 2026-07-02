package com.rivals.skillsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rivals.skillsim.data.api.RetrofitProvider
import com.rivals.skillsim.data.local.UserPrefsDataStore
import com.rivals.skillsim.data.local.userPrefsDataStore
import com.rivals.skillsim.data.repository.SkillRepository

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        SkillRepository(RetrofitProvider.createSkillApi())
    }

    private val userPrefs by lazy {
        UserPrefsDataStore(userPrefsDataStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RivalsSkillSimApp(
                repository = repository,
                userPrefsDataStore = userPrefs,
            )
        }
    }
}
