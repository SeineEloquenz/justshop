package nz.eloque.justshop.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.justshop.R

@Composable
fun AboutView() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Image(
                imageVector = Icons.Default.ShoppingBag,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                contentDescription = stringResource(R.string.justshop),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            Text(
                text = stringResource(R.string.justshop),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall
            )
            AboutContent(
                icon = Icons.Default.Construction,
                text = stringResource(R.string.made_with_love),
                textStyle = MaterialTheme.typography.labelLarge
            )
        }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/SeineEloquenz/justshop") },
            modifier = Modifier.fillMaxWidth().padding(50.dp, 0.dp)
        ) {
            AboutContent(icon = Icons.Default.Source, text = stringResource(R.string.source_code))
        }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/SeineEloquenz/justshop/blob/main/LICENSE") },
            modifier = Modifier.fillMaxWidth().padding(50.dp, 0.dp)) {
            AboutContent(icon = Icons.Default.Description, text = stringResource(R.string.license))
        }
    }
}

@Composable
fun AboutContent(
    icon: ImageVector,
    text: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.secondary,
            style = textStyle,
            textAlign = TextAlign.Center
        )
    }
}