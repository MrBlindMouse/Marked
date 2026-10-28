package com.bmdstudios.marked.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmdstudios.marked.editor.MarkedRecipe

/**
 * Recipes screen: lists saved mark recipes and lets the user apply one to the mark editor.
 * Back returns to the landing screen.
 */
@Composable
fun MarkedRecipesScreen(
    recipes: List<MarkedRecipe>,
    onApplyRecipe: (MarkedRecipe) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "<-Back")
            }
            Text(
                text = "Recipes",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Saved mark recipes. Apply one to build a mark set.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (recipes.isEmpty()) {
            Text(
                text = "No saved recipes yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        recipes.forEach { recipe ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recipe.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { onApplyRecipe(recipe) }) {
                    Text(text = "Apply")
                }
            }
        }
    }
}
