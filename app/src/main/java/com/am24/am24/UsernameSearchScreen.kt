package com.am24.am24

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSearchScreen(
    navController: NavController,
    viewModel: UsernameSearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                title = { Text(stringResource(R.string.settings_search_username_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.settings_search_username_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                results.isEmpty() && query.isNotBlank() -> {
                    Text(
                        text = stringResource(R.string.settings_search_username_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.settings_search_username_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results, key = { it.userId }) { profile ->
                            UsernameSearchResultRow(
                                profile = profile,
                                onClick = {
                                    val encoded = Uri.encode(profile.userId)
                                    navController.navigate("previewUserProfile/$encoded")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsernameSearchResultRow(
    profile: Profile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            leadingContent = { UsernameSearchAvatar(profile) },
            headlineContent = {
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

@Composable
private fun UsernameSearchAvatar(profile: Profile) {
    val imageUrl = profile.profilepicThumbnailUrl ?: profile.profilepicUrl
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = profile.username,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.local_placeholder),
                contentDescription = profile.username,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

class UsernameSearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Profile>>(emptyList())
    val results: StateFlow<List<Profile>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var cachedProfiles: List<Profile>? = null

    init {
        viewModelScope.launch {
            query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { value ->
                    val normalized = value.trim().removePrefix("@")
                    if (normalized.length < 2) {
                        _results.value = emptyList()
                        _isLoading.value = false
                        _error.value = null
                        return@collectLatest
                    }

                    _isLoading.value = true
                    _error.value = null

                    try {
                        val matches = withContext(Dispatchers.IO) {
                            searchProfiles(normalized)
                        }
                        _results.value = matches
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Unknown error"
                        _results.value = emptyList()
                    } finally {
                        _isLoading.value = false
                    }
                }
        }
    }

    fun updateQuery(value: String) {
        _query.value = value
    }

    private suspend fun searchProfiles(query: String): List<Profile> {
        val lower = query.lowercase(Locale.getDefault())
        val cachedMatches = ProfileCache.knownProfiles.value.values.filter { profile ->
            profile.username.lowercase(Locale.getDefault()).contains(lower)
        }

        val remoteProfiles = loadProfiles()
        val remoteMatches = remoteProfiles.filter { profile ->
            profile.username.lowercase(Locale.getDefault()).contains(lower)
        }

        val combined = (cachedMatches + remoteMatches)
            .filter { it.userId.isNotBlank() && it.username.isNotBlank() }
            .distinctBy { it.userId }
            .sortedWith(compareBy(
                { !it.username.equals(query, ignoreCase = true) },
                { !it.username.startsWith(query, ignoreCase = true) },
                { it.username.lowercase(Locale.getDefault()) }
            ))

        return combined.take(30)
    }

    private suspend fun loadProfiles(): List<Profile> {
        val cached = cachedProfiles
        if (cached != null) return cached

        val snapshot = FirebaseRefs.db.getReference("users").get().await()
        val profiles = snapshot.children.mapNotNull { child ->
            val id = child.key ?: return@mapNotNull null
            val profile = child.getValue(Profile::class.java) ?: return@mapNotNull null
            if (profile.userId.isBlank()) profile.copy(userId = id) else profile
        }
        cachedProfiles = profiles
        return profiles
    }
}