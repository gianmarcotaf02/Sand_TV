package it.sandtv.app.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.ProfileDao
import it.sandtv.app.data.database.entity.Profile
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.ui.loading.SplashScreen
import it.sandtv.app.ui.setup.SetupActivity
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Profile Selection Activity - App entry point
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class ProfileSelectionActivity : ComponentActivity() {
    
    @Inject lateinit var profileDao: ProfileDao
    @Inject lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SandTVDebug", "ProfileSelectionActivity onCreate STARTED")
        
        setContent {
            SandTVTheme {
                // Show splash on first launch, then profile selection
                var showSplash by remember { mutableStateOf(true) }
                
                if (showSplash) {
                    SplashScreen(
                        onComplete = { 
                            android.util.Log.d("SandTVDebug", "Splash complete, showing content")
                            showSplash = false 
                        }
                    )
                } else {
                    ProfileSelectionContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("SandTVDebug", "ProfileSelectionActivity onResume")
    }
    
    @Composable
    private fun ProfileSelectionContent() {
        // State
        var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
        var lastUsedProfileId by remember { mutableStateOf<Long?>(null) }
        var showAddDialog by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showOptionsDialog by remember { mutableStateOf(false) }
        var selectedProfile by remember { mutableStateOf<Profile?>(null) }
        
        // Load profiles on first composition
        LaunchedEffect(Unit) {
            android.util.Log.d("SandTVDebug", "ProfileSelectionActivity LaunchedEffect STARTED")
            checkAutoStartAndLoadProfiles { loadedProfiles, lastId ->
                android.util.Log.d("SandTVDebug", "Profiles loaded: ${loadedProfiles.size}, LastId: $lastId")
                profiles = loadedProfiles
                lastUsedProfileId = lastId
            }
        }
        
        // Main Screen
        ProfileSelectionScreen(
            profiles = profiles,
            onProfileSelected = { profile ->
                selectProfile(profile)
            },
            onProfileLongClick = { profile ->
                selectedProfile = profile
                showOptionsDialog = true
            },
            onAddProfile = {
                showAddDialog = true
            },
            preSelectedProfileId = lastUsedProfileId  // Pre-focus last used profile
        )
        
        // Dialogs
        AddProfileDialog(
            isVisible = showAddDialog,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, avatarIndex ->
                showAddDialog = false
                createProfile(name, avatarIndex) { profiles = it }
            }
        )
        
        EditProfileDialog(
            isVisible = showEditDialog,
            profile = selectedProfile,
            onDismiss = { 
                showEditDialog = false
                selectedProfile = null
            },
            onConfirm = { updatedProfile ->
                showEditDialog = false
                updateProfile(updatedProfile) { profiles = it }
                selectedProfile = null
            }
        )
        
        DeleteProfileDialog(
            isVisible = showDeleteDialog,
            profile = selectedProfile,
            onDismiss = {
                showDeleteDialog = false
                selectedProfile = null
            },
            onConfirm = { profileToDelete ->
                showDeleteDialog = false
                deleteProfile(profileToDelete) { profiles = it }
                selectedProfile = null
            }
        )
        
        ProfileOptionsDialog(
            isVisible = showOptionsDialog,
            profile = selectedProfile,
            onDismiss = {
                showOptionsDialog = false
                selectedProfile = null
            },
            onEdit = { profile ->
                showOptionsDialog = false
                selectedProfile = profile
                showEditDialog = true
            },
            onDelete = { profile ->
                showOptionsDialog = false
                selectedProfile = profile
                showDeleteDialog = true
            }
        )
    }
    
    private fun checkAutoStartAndLoadProfiles(onProfilesLoaded: (List<Profile>, Long?) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            var profiles = profileDao.getAllProfiles().first()
            
            // Fetch last used profile ID on IO
            val lastProfileId = userPreferences.getCurrentProfileId()
            
            if (profiles.isEmpty()) {
                // Create default profile first
                val defaultProfile = Profile(
                    name = "Principale",
                    avatarIndex = 0,
                    isDefault = true
                )
                profileDao.insert(defaultProfile)
                profiles = profileDao.getAllProfiles().first()
            }
            
            // Auto-start with last profile if enabled
            val autoStartMode = userPreferences.getAutoStartMode()
            if (autoStartMode == "last") {
                if (lastProfileId != null && lastProfileId > 0) {
                    val profile = profileDao.getProfileById(lastProfileId)
                    if (profile != null) {
                        withContext(Dispatchers.Main) {
                            goToMain(profile)
                        }
                        return@launch
                    }
                }
            }
            
            // Show profile selection
            withContext(Dispatchers.Main) {
                onProfilesLoaded(profiles, lastProfileId)
            }
        }
    }
    
    private fun selectProfile(profile: Profile) {
        // Fire-and-forget save of last used profile to prevent blocking UI
        lifecycleScope.launch(Dispatchers.IO) {
            android.util.Log.d("SandTVDebug", "Saving profile ID ${profile.id} in background...")
            userPreferences.setCurrentProfileId(profile.id)
        }
        
        // Immediate navigation
        goToMain(profile)
    }
    
    private fun goToMain(profile: Profile) {
        android.util.Log.d("SandTVDebug", "Navigating to LoadingActivity immediately. Setup check delegated.")
        
        // Always go to LoadingActivity first - it handles redirection to Setup if needed
        // This avoids blocking the UI thread waiting for DataStore reads
        val intent = Intent(this, it.sandtv.app.ui.loading.LoadingActivity::class.java).apply {
            putExtra("profile_id", profile.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        startActivity(intent)
        finish()
        // Remove transition animation to feel faster
        overridePendingTransition(0, 0)
    }
    
    private fun createProfile(name: String, avatarIndex: Int, onComplete: (List<Profile>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = Profile(
                name = name,
                avatarIndex = avatarIndex
            )
            profileDao.insert(profile)
            val profiles = profileDao.getAllProfiles().first()
            withContext(Dispatchers.Main) {
                onComplete(profiles)
            }
        }
    }
    
    private fun updateProfile(profile: Profile, onComplete: (List<Profile>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            profileDao.update(profile)
            val profiles = profileDao.getAllProfiles().first()
            withContext(Dispatchers.Main) {
                onComplete(profiles)
            }
        }
    }
    
    private fun deleteProfile(profile: Profile, onComplete: (List<Profile>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            profileDao.delete(profile)
            val profiles = profileDao.getAllProfiles().first()
            withContext(Dispatchers.Main) {
                onComplete(profiles)
            }
        }
    }
}
