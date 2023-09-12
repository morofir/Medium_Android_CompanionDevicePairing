    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.recyclerview.widget.DiffUtil
    import androidx.recyclerview.widget.ListAdapter
    import androidx.recyclerview.widget.RecyclerView
    import com.rick.companiondevicepairing.R

    // Replace `String` with your specific data type if it's not a string.
    class MyLogAdapter : ListAdapter<String, MyLogAdapter.ViewHolder>(LogDiffCallback()) {

        // ViewHolder definition
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val logTextView: TextView = itemView.findViewById(R.id.logTextView)
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
            val log = getItem(position)
            holder.logTextView.text = log
        }

         fun submitListItem(newList: List<String>) {
            super.submitList(newList.toMutableList())
        }
    }
