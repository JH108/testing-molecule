package com.example.testingmolecule

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionClock.ContextClock
import app.cash.molecule.launchMolecule
import com.example.testingmolecule.ui.theme.MoleculeFormsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MoleculeFormViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoleculeFormsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Form
                    Form(viewModel = viewModel)
                }
            }
        }
    }
}

// Form
@Composable
fun Form(
    viewModel: MoleculeFormViewModel
) {
    val model by viewModel.models.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        FormView(model = model, onEvent = { viewModel.take(it) })
    }
}

@Composable
fun FormView(
    model: DataModel,
    onEvent: (Event) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (model.loading) {
            // Show loading
            CircularProgressIndicator()
        } else {
            // Show form
            FormContent(model = model, onEvent = onEvent)
        }
    }
}

@Composable
fun FormContent(
    model: DataModel,
    onEvent: (Event) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        model.fields.forEach { (id, field) ->
            FieldView(
                field = field,
                value = model.data[id] ?: "",
                onEvent = onEvent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldView(
    field: Field,
    value: String,
    onEvent: (Event) -> Unit = {}
) {
    OutlinedTextField(
        label = { Text(text = field.title) },
        value = value,
        onValueChange = {
            onEvent(
                Event.UpdateFormData(
                    mapOf(field.id to it)
                )
            )
        }
    )
}

class MoleculeFormViewModel : MoleculeViewModel<Event, DataModel>() {
    @Composable
    override fun models(events: Flow<Event>): DataModel {
        return FormPresenter(
            events = events
        )
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun FormPresenter(events: Flow<Event>): DataModel {
    var loading by remember {
        mutableStateOf(true)
    }
    var fields by remember {
        mutableStateOf(emptyMap<Long, Field>())
    }
    var data by remember {
        mutableStateOf(emptyMap<Long, String>())
    }

    LaunchedEffect(key1 = Unit, block = {
        // Create initial fields
        fields = buildMap {
            repeat(10) {
                put(it.toLong(), Field(title = "Field $it", id = it.toLong()))
            }
        }
        delay(1000)
        loading = false
    })

    LaunchedEffect(key1 = events, block = {
        // Handle the events
        events.collect {
            when (it) {
                is Event.UpdateFormData -> {
                    data = data + it.data
                }

                is Event.UpdateFormAttributes -> {
                    fields = fields + it.attributes
                }

                is Event.Cancel -> {
                    // Cancel the form
                    data = emptyMap()
                    fields = emptyMap()
                    loading = true
                }
            }
        }
    })

    return DataModel(
        loading = loading,
        fields = fields,
        data = data
    )
}

abstract class MoleculeViewModel<Event, Model> : ViewModel() {
    private val scope = CoroutineScope(viewModelScope.coroutineContext + AndroidUiDispatcher.Main)

    // Events have a capacity large enough to handle simultaneous UI events, but
    // small enough to surface issues if they get backed up for some reason.
    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 20)

    val models: StateFlow<Model> by lazy(LazyThreadSafetyMode.NONE) {
        scope.launchMolecule(clock = ContextClock) {
            models(events)
        }
    }

    fun take(event: Event) {
        if (!events.tryEmit(event)) {
            error("Event buffer overflow.")
        }
    }

    @Composable
    protected abstract fun models(events: Flow<Event>): Model
}

sealed interface Event {
    data class UpdateFormData(val data: Map<Long, String>) : Event
    data class UpdateFormAttributes(
        val attributes: Map<Long, Field>
    ) : Event

    object Cancel : Event
}

data class DataModel(
    val loading: Boolean = false,
    val fields: Map<Long, Field> = emptyMap(),
    val data: Map<Long, String> = emptyMap(),
)

data class Field(
    val title: String,
    val id: Long
)
