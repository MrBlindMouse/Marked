package com.bmdstudios.marked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bmdstudios.marked.editor.MarkedEditorScreen
import com.bmdstudios.marked.ui.theme.MarkedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarkedTheme {
                MarkedEditorScreen(onExit = { finish() })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MarkedEditorScreenPreview() {
    MarkedTheme {
        MarkedEditorScreen()
    }
}
