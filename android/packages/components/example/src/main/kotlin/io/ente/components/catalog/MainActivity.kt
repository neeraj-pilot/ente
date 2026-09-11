package io.ente.components.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import io.ente.components.EnteSpacing
import io.ente.components.EnteTheme
import io.ente.components.EnteTypography
import io.ente.components.LocalEntePalette

internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnteTheme {
                val palette = LocalEntePalette.current
                Surface(
                    color = palette.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier.padding(EnteSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(EnteSpacing.sm),
                    ) {
                        Text(
                            text = "Components",
                            color = palette.text,
                            style = EnteTypography.display2,
                        )
                        Text(
                            text = "Native foundations for Ente apps",
                            color = palette.mutedText,
                            style = EnteTypography.body,
                        )
                    }
                }
            }
        }
    }
}
