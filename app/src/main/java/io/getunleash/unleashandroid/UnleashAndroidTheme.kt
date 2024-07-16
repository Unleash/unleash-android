package io.getunleash.unleashandroid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource

@Composable
fun UnleashAndroidTheme(content: @Composable () -> Unit) {

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = colorResource(id = R.color.purple_500),
            onPrimary = colorResource(id = R.color.white),
            secondary = colorResource(id = R.color.teal_200),
            onSecondary = colorResource(id = R.color.black)
        ),
        content = content
    )
}