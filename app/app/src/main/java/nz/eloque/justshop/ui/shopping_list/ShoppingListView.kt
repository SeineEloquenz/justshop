package nz.eloque.justshop.ui.shopping_list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nz.eloque.justshop.R
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import java.util.UUID

@Composable
fun ShoppingListView(
    shoppingListViewModel: ShoppingListViewModel,
    modifier: Modifier = Modifier
) {

    val shoppingListUiState by shoppingListViewModel.uiState.collectAsState()
    val sortedList = shoppingListUiState.items.sortedBy { it.timestamp }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom
    ) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = shoppingListUiState.items.size)
        val coroutineScope = rememberCoroutineScope()
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .weight(9f)
        ) {
            coroutineScope.launch {
                listState.animateScrollToItem(shoppingListUiState.items.size)
            }
            items(sortedList) { item ->
                Row(
                    modifier = modifier.fillMaxWidth(),
                ) {
                    ShoppingItemCard(
                        itemContent = item.content,
                        checkedState = item.checked,
                        onCheckedChange = {
                            shoppingListViewModel.viewModelScope.launch(Dispatchers.IO) {
                                shoppingListViewModel.updateItem(item.copy(checked = it))
                            }
                        },
                        onEdit = {
                            shoppingListViewModel.viewModelScope.launch(Dispatchers.IO) {
                                shoppingListViewModel.updateItem(item.copy(content = it))
                            }
                        },
                    )
                }
            }
        }
        AddItemField(onSubmit = {
            shoppingListViewModel.viewModelScope.launch(Dispatchers.IO) {
                shoppingListViewModel.updateItem(ShoppingItem(UUID.randomUUID(), it, checked = false))
            }
        })
    }
}

@Composable
fun AddItemField(
    initialValue: String = "",
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val newItem = rememberSaveable { mutableStateOf(initialValue) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        val maxWidth = maxWidth
        Row {
            OutlinedTextField(
                label = { Text(stringResource(id = R.string.item)) },
                value = newItem.value,
                onValueChange = { newItem.value = it },
                singleLine = true,
                modifier = Modifier
                    .width(maxWidth * 0.8f)
                    .heightIn(0.dp, 150.dp)
            )
            SubmitButton(
                newItem = newItem,
                onSubmit = onSubmit,
                modifier = modifier
                    .width(maxWidth * 0.8f)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
fun SubmitButton(
    newItem: MutableState<String>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        enabled = newItem.value != "",
        onClick = {
            onSubmit.invoke(newItem.value)
            newItem.value = ""
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.submit)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemCard(
    itemContent: String,
    checkedState: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
    onEdit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val openEditDialog = remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
            .alpha(if (checkedState) 0.25f else 1.0f)
            .combinedClickable(
                onClick = {
                    onCheckedChange.invoke(!checkedState)
                },
                onLongClick = {
                    openEditDialog.value = true
                },
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Checkbox(
                checked = checkedState,
                onCheckedChange = null
            )
            Text(
                text = itemContent,
            )
        }
    }
    if (openEditDialog.value) {
        Dialog(
            onDismissRequest = { openEditDialog.value = false }
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    AddItemField(
                        initialValue = itemContent,
                        onSubmit = onEdit,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ShoppingItemCardPreview() {
    ShoppingItemCard("hello", false)
}