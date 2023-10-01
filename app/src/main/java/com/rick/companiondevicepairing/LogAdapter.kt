    import android.util.Log
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.recyclerview.widget.DiffUtil
    import androidx.recyclerview.widget.ListAdapter
    import androidx.recyclerview.widget.RecyclerView
    import com.rick.companiondevicepairing.R
    import java.text.ParseException
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
                return oldItem == newItem
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

            val splitString = ": Device State: "
            val splitIndex = fullLog.indexOf(splitString)
            if (splitIndex == -1) {
                // Log entry does not have the expected format, handle gracefully
                holder.logTextView.text = fullLog
                holder.timestampTextView.text = "N/A"
                return
            }

            val timestamp = fullLog.substring(0, splitIndex)
            val logMessage = fullLog.substring(splitIndex + splitString.length)

            holder.logTextView.text = logMessage

            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            try {
                val logDate = format.parse(timestamp)
                holder.timestampTextView.text = format.format(logDate)
            } catch (e: ParseException) {
                Log.e("MyLogAdapter", "Failed to parse date: $timestamp", e)
                holder.timestampTextView.text = timestamp
            }
        }


        fun submitListItem(newList: List<String>) {
            super.submitList(newList.toMutableList())
        }
    }
