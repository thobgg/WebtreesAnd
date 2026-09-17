package de.bgghome.webtrees.nativ

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.bgghome.webtrees.nativ.ui.AppRoot
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.WtTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleConnectLink(intent)
        setContent {
            WtTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleConnectLink(intent)
    }

    /** "Verbinden" aus webtrees: Adresse, Baum und Einmal-Code kommen im Link - nichts davon muss getippt werden. */
    private fun handleConnectLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "webtreesand" || uri.host != "connect") return

        viewModel.connect(
            url = uri.getQueryParameter("url").orEmpty(),
            tree = uri.getQueryParameter("tree").orEmpty(),
            code = uri.getQueryParameter("code").orEmpty(),
        )
        // Den Code nicht im Intent liegen lassen (er waere nach einer Drehung des Bildschirms ohnehin verbraucht).
        intent.data = null
    }
}
