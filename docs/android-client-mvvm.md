# Android (Kotlin + Jetpack) MVVM Blueprint

This document outlines a **native Android** client built with **Kotlin**, **Jetpack**, **MVVM**, and **coroutines**. It aligns with the requested architecture:

```
┌──────────────────────────┐
│          View            │
│  (Activity / Fragment)   │
│                          │
│  - Displays UI           │
│  - Sends user events     │
│  - Observes state        │
└───────────▲──────────────┘
            │ observes
            │ StateFlow / LiveData
            │
┌───────────┴──────────────┐
│        ViewModel         │
│                          │
│  - Holds UI state        │
│  - Calls repositories    │
│  - Runs coroutines       │
│  - No Android UI refs    │
└───────────▲──────────────┘
            │ calls
            │
┌───────────┴──────────────┐
│          Model           │
│                          │
│  Repository              │
│   ├─ REST API (Retrofit) │
│   └─ Room Database       │
└──────────────────────────┘
```

## Suggested module/package layout

```
app/
  ui/
    auth/
    farm/
    scouting/
    analytics/
  data/
    local/          // Room
    remote/         // Retrofit
    repository/     // Data orchestration
  domain/
    model/
    usecase/
  di/
```

## Dependencies (Gradle)

```kotlin
// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// Lifecycle / StateFlow
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// JSON
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
```

## Model layer

### Retrofit API

```kotlin
interface PestScoutApi {
    @POST("/api/auth/login")
    suspend fun login(@Body payload: LoginRequest): LoginResponse

    @GET("/api/farms")
    suspend fun getFarms(): List<FarmDto>
}
```

### Room entities + DAO

```kotlin
@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val licensedArea: Double?,
    val updatedAt: Instant
)

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms ORDER BY name")
    fun observeFarms(): Flow<List<FarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FarmEntity>)
}
```

### Repository

```kotlin
class FarmRepository(
    private val api: PestScoutApi,
    private val farmDao: FarmDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val farms: Flow<List<FarmEntity>> = farmDao.observeFarms()

    suspend fun refreshFarms() = withContext(ioDispatcher) {
        val remote = api.getFarms()
        val entities = remote.map { dto ->
            FarmEntity(
                id = dto.id,
                name = dto.name,
                licensedArea = dto.licensedArea,
                updatedAt = dto.updatedAt
            )
        }
        farmDao.upsertAll(entities)
    }
}
```

## ViewModel layer

```kotlin
data class FarmUiState(
    val isLoading: Boolean = false,
    val farms: List<FarmEntity> = emptyList(),
    val error: String? = null
)

class FarmViewModel(
    private val repository: FarmRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FarmUiState())
    val state: StateFlow<FarmUiState> = _state.asStateFlow()

    init {
        observeFarms()
        refresh()
    }

    private fun observeFarms() {
        viewModelScope.launch {
            repository.farms.collect { farms ->
                _state.update { it.copy(farms = farms) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.refreshFarms() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
```

## View layer (Fragment)

```kotlin
class FarmFragment : Fragment(R.layout.fragment_farm) {
    private val viewModel: FarmViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = FarmAdapter()
        val recyclerView = view.findViewById<RecyclerView>(R.id.farm_list)
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.farms)
                    view.findViewById<View>(R.id.progress)
                        .isVisible = state.isLoading
                    view.findViewById<TextView>(R.id.error)
                        .text = state.error ?: ""
                }
            }
        }

        view.findViewById<SwipeRefreshLayout>(R.id.refresh)
            .setOnRefreshListener {
                viewModel.refresh()
            }
    }
}
```

## Activity entry point

```kotlin
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FarmFragment())
                .commit()
        }
    }
}
```

In the real project you would also define `activity_main.xml` with a
`FragmentContainerView` named `fragment_container`, and register the activity
in `AndroidManifest.xml` as the launcher activity.

## Notes for offline-first sync

- Persist server data in Room and expose it via `Flow` to the ViewModel.
- Use a `sync` service to push pending writes when the network is available.
- Use `clientRequestId` for idempotent writes and optimistic concurrency (`version`) when calling backend sync endpoints.
- Store sync metadata locally (last sync timestamp, pending session/observation queue, etc.).

## Mapping to backend endpoints

Suggested mapping for key areas (based on the API surface in this repository):

- **Auth**: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/me`
- **Farm data**: `/api/farms`, `/api/farms/{id}`
- **Scouting**: `/api/scouting/farms/{farmId}/sessions`, `/api/scouting/farms/{farmId}/sync`
- **Photos**: `/api/scouting/photos/register`, `/api/scouting/photos/confirm`

## Summary

This pattern keeps UI logic in the View, state handling and business logic in the ViewModel, and data orchestration in repositories with Retrofit + Room. Coroutines and StateFlow provide lifecycle-aware updates and asynchronous execution while maintaining a clean separation between layers.

## How to run

This repository only provides **sample Kotlin files** under `docs/android-client-sample`.
To run it as a real Android app, create a new Android project in Android Studio
and copy the package tree plus resources:

1. **Create a new Android app project** in Android Studio (Empty Activity).
2. **Copy Kotlin files** from `docs/android-client-sample/src/main/kotlin` into
   your new app's `src/main/kotlin` (matching package names).
3. **Add layouts**:
   - `activity_main.xml` with a `FragmentContainerView` using
     `@+id/fragment_container`
   - `fragment_farm.xml` with RecyclerView (`@+id/farm_list`), a progress view
     (`@+id/progress`), error TextView (`@+id/error`), and
     `SwipeRefreshLayout` (`@+id/refresh`)
   - `item_farm.xml` with a `TextView` for the row label
4. **Wire dependencies** (Room, Retrofit, coroutines, lifecycle, Moshi) in
   `build.gradle`.
5. **Add DI** (Hilt or manual) to provide `PestScoutApi`, `AppDatabase`, and
   `FarmRepository` to the ViewModel.
6. **Run** the app in Android Studio on an emulator/device.
