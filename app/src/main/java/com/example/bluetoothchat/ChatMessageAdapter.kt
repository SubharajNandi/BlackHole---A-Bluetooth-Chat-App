package com.example.bluetoothchat

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView

class ChatMessageAdapter(
    context: Context,
    private val messageList: MutableList<ChatMessage>
) : ArrayAdapter<ChatMessage>(context, 0, messageList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false)
        val message = getItem(position) ?: return view

        val rootLayout = view.findViewById<LinearLayout>(R.id.layout_item_root)
        val bubbleContainer = view.findViewById<LinearLayout>(R.id.layout_bubble_container)
        val tvSender = view.findViewById<TextView>(R.id.text_sender)
        val tvMessage = view.findViewById<TextView>(R.id.text_message)

        tvMessage.text = message.text

        val params = bubbleContainer.layoutParams as LinearLayout.LayoutParams

        if (message.isMe) {
            rootLayout.gravity = Gravity.END
            bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_sent)
            tvSender.visibility = View.GONE
            tvMessage.setTextColor(Color.WHITE)

            params.setMargins(100, 0, 0, 0)
        } else {
            rootLayout.gravity = Gravity.START
            bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_received)
            if (message.senderName.isNotEmpty()) {
                tvSender.visibility = View.VISIBLE
                tvSender.text = message.senderName
            } else {
                tvSender.visibility = View.GONE
            }
            tvMessage.setTextColor(Color.WHITE)

            params.setMargins(0, 0, 100, 0)
        }
        bubbleContainer.layoutParams = params

        return view
    }
}
