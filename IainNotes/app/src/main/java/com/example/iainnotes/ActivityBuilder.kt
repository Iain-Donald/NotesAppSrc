import android.app.Activity
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.example.iainnotes.R

object ActivityBuilder {

	/** Inflates the shared themed input field, optionally prefilled. */
	fun input(activity: Activity, prefill: String? = null): EditText {
		val ctx = ContextThemeWrapper(activity, R.style.RoundedDialog)
		val view = activity.layoutInflater.cloneInContext(ctx)
			.inflate(R.layout.item_dialogalert, null) as EditText
		prefill?.let { view.setText(it) }
		return view
	}

	/** Themed builder — use in place of AlertDialog.Builder(this). */
	fun dialog(activity: Activity): AlertDialog.Builder =
		AlertDialog.Builder(activity, R.style.RoundedDialog)
}