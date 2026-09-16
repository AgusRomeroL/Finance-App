package mx.budget.ui.quicktap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla que precede al permiso de mostrarse sobre otras apps
 * (especificación §3.4).
 *
 * Enseña el panel antes de pedir nada y dice los tres pasos con las palabras que
 * aparecen en Ajustes, porque el recorrido no termina en el permiso: después hay
 * que elegir la app en el gesto del sistema. Cierra con lo que la gente teme de
 * este permiso, que es que la app mire lo que hay en pantalla.
 *
 * "Ahora no" no es un callejón sin salida: el gasto rápido sigue funcionando
 * como hoja completa.
 */
@Composable
fun OverlayPermissionScreen(
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            "GASTO RÁPIDO",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Dos golpes en la espalda del teléfono",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(24.dp))
        Vistazo()

        Spacer(Modifier.height(26.dp))
        Paso(1, "Android necesita tu permiso para que la app se muestre encima de otras. Es lo único que se pide.")
        Spacer(Modifier.height(18.dp))
        Paso(2, "Después, en Ajustes, Sistema, Gestos, Toque rápido, eliges abrir Presupuesto Familiar.")
        Spacer(Modifier.height(18.dp))
        Paso(3, "Listo: dos golpes y anotas el gasto sin salir de lo que estabas haciendo.")

        Spacer(Modifier.height(26.dp))
        Text(
            "El panel solo aparece cuando tú lo llamas y se cierra solo a los seis segundos. " +
                "No lee nada de lo que tengas en pantalla.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        BotonAncho("Dar el permiso", principal = true, onClick = onGrant)
        Spacer(Modifier.height(12.dp))
        BotonAncho("Ahora no", principal = false, onClick = onSkip)
    }
}

/** Miniatura del panel: enseñar antes de pedir. */
@Composable
private fun Vistazo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "GASTO RÁPIDO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$1,950",
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipMini("Despensa")
                ChipMini("Banamex")
            }
        }
    }
}

@Composable
private fun ChipMini(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Paso(numero: Int, texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                numero.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BotonAncho(texto: String, principal: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (principal) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (principal) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
