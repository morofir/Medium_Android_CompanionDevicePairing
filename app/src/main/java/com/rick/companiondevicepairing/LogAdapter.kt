    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.recyclerview.widget.DiffUtil
    import androidx.recyclerview.widget.ListAdapter
    import androidx.recyclerview.widget.RecyclerView
    import com.rick.companiondevicepairing.R
    import java.text.SimpleDateFormat
    import java.util.*

    class MyLogAdapter : ListAdapter<String, MyLogAdapter.ViewHolder>(LogDiffCallback()) {

        // ViewHolder definition
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val logTextView: TextView = itemView.findViewById(R.id.logTextView)
            val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)

        }

        // DiffUtil for efficient list updates
        class LogDiffCallback : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem // Replace with your unique ID comparison logic
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem // Replace with your content comparison logic
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.log_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val fullLog = getItem(position)

            // Split the log to retrieve timestamp and message
            val splitIndex = fullLog.indexOf(": ")
            if (splitIndex == -1) {
                // Log entry does not have the expected format, handle gracefully
                holder.logTextView.text = fullLog
                holder.timestampTextView.text = "N/A"
                return
            }

            val timestamp = fullLog.substring(0, splitIndex)
            val logMessage = fullLog.substring(splitIndex + 2)

            holder.logTextView.text = logMessage

            // Convert the timestamp to "HH:mm" format for display
            val originalFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val logDate = originalFormat.parse(timestamp)
            val displayTime = if (logDate != null) displayFormat.format(logDate) else "N/A"

            holder.timestampTextView.text = displayTime
        }


        fun submitListItem(newList: List<String>) {
            super.submitList(newList.toMutableList())
        }
    }
