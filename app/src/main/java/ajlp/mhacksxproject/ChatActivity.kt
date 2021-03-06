package ajlp.mhacksxproject

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.gson.JsonObject
import com.koushikdutta.async.future.FutureCallback
import com.koushikdutta.ion.Ion
import com.twilio.chat.*
import kotlinx.android.synthetic.main.activity_chat.*

// Class seems vaguely similar to Main, should separate server access in its own component.
class ChatActivity:AppCompatActivity(){

    private var mMessagesRecyclerView:RecyclerView? = null
    private var mMessagesAdapter:MessagesAdapter? = null
    private var mWriteMessageEditText:EditText? = null
    private var mSendChatMessageButton: ImageButton? = null
    private var mChatClient:ChatClient? = null
    private var mGeneralChannel:Channel? = null


    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        setTitle("Stacy")

        mMessagesRecyclerView = messagesRecyclerView
        val layoutManager = LinearLayoutManager(this)
        // for a chat app, show latest at the bottom
        layoutManager.setStackFromEnd(true)
        mMessagesRecyclerView?.setLayoutManager(layoutManager)
        mMessagesAdapter = MessagesAdapter()
        mMessagesRecyclerView?.setAdapter(mMessagesAdapter)
        mWriteMessageEditText = writeMessageEditText
        mWriteMessageEditText!!.getBackground().setColorFilter(ContextCompat.getColor(applicationContext, R.color.textOnPrimary), PorterDuff.Mode.SRC_IN);

        retrieveAccessTokenfromServer()
        mSendChatMessageButton = sendChatMessageButton
        mSendChatMessageButton?.setOnClickListener(object:View.OnClickListener {
            override fun onClick(view:View) {
                if (mGeneralChannel != null)
                {

                    val messageBody = (mWriteMessageEditText as EditText?)?.text.toString()
                    val message = Message.options().withBody(messageBody)
                    Log.d(TAG, "Message created")
                    val listener: CallbackListener<Message> =
                            object:CallbackListener<Message>() {
                                override fun onSuccess(p0: Message?) {
                                    runOnUiThread(object : Runnable {
                                        public override fun run() {
                                            // need to modify user interface elements on the UI thread
                                            (mWriteMessageEditText as TextView).text = ""
                                        }
                                    })
                                }
                            }
                    mGeneralChannel!!.messages.sendMessage(message, listener)
                }
            }
        })
    }


    private fun retrieveAccessTokenfromServer() {
        val tokenURL = SERVER_TOKEN_URL + "/" + getString(R.string.username)
        Ion.with(this)
                .load(tokenURL)
                .asJsonObject()
                .setCallback(object:FutureCallback<JsonObject> {
                    override fun onCompleted(e:Exception?, result:JsonObject?) {
                        if (e == null)
                        {
                            val identity = result?.get("identity")?.getAsString()
                            val accessToken = result?.get("token")?.getAsString()
                            title = getString(R.string.otheruser)
                            val builder = ChatClient.Properties.Builder()
                            val props = builder.createProperties()
                            ChatClient.create(this@ChatActivity, accessToken!!, props, mChatClientCallback)
                        }
                        else
                        {
                            Toast.makeText(this@ChatActivity,
                                    "ERROR RECEIVING TOKEN", Toast.LENGTH_SHORT)
                                    .show()
                        }
                    }
                })
    }
    private fun loadChannels() {
        mChatClient?.getChannels()?.getChannel(DEFAULT_CHANNEL_NAME, object:CallbackListener<Channel>() {
            override fun onSuccess(channel:Channel) {
                if (channel != null)
                {
                    joinChannel(channel)
                }
                else
                {
                    mChatClient!!.getChannels().createChannel(DEFAULT_CHANNEL_NAME,
                            Channel.ChannelType.PUBLIC, object:CallbackListener<Channel>() {
                        override fun onSuccess(channel:Channel) {
                            if (channel != null)
                            {
                                joinChannel(channel)
                            }
                        }
                        override fun onError(errorInfo:ErrorInfo) {
                            Log.e(TAG, "Error creating channel: " + errorInfo.getMessage())
                        }
                    })
                }
            }
            override fun onError(errorInfo:ErrorInfo) {
                Log.e(TAG, "Error retrieving channel: " + errorInfo.getMessage())
            }
        })
    }
    private fun joinChannel(channel:Channel) {
        Log.d(TAG, "Joining Channel: " + channel.getUniqueName())
        channel.join(object:StatusListener() {
            override fun onSuccess() {
                mGeneralChannel = channel
                Log.d(TAG, "Joined default channel")
                mGeneralChannel!!.addListener(mDefaultChannelListener)
            }
            override fun onError(errorInfo:ErrorInfo) {
                Log.e(TAG, "Error joining channel: " + errorInfo.getMessage())
            }
        })
    }
    private val mChatClientCallback = object:CallbackListener<ChatClient>() {
        override fun onSuccess(chatClient:ChatClient) {
            mChatClient = chatClient
            loadChannels()
            Log.d(TAG, "Success creating Twilio Chat Client")
        }
        override fun onError(errorInfo:ErrorInfo) {
            Log.e(TAG, "Error creating Twilio Chat Client: " + errorInfo.getMessage())
        }
    }
    private val mDefaultChannelListener = object:ChannelListener {

        override fun onMemberUpdated(member: Member?, p1: Member.UpdateReason?) {
            Log.d(TAG, "Member updated: " + member?.getIdentity())

        }

        override fun onMessageUpdated(message: Message?, p1: Message.UpdateReason?) {
            Log.d(TAG, "Message updated: " + message?.getMessageBody())

        }

        override fun onMessageAdded(message:Message) {
            Log.d(TAG, "Message added (CHAT)")
            runOnUiThread(object:Runnable {
                public override fun run() {
                    // need to modify user interface elements on the UI thread
                    UserData.Messages.add(message)
                    mMessagesAdapter?.notifyDataSetChanged()
                }
            })
        }
        override fun onMessageDeleted(message:Message) {
            Log.d(TAG, "Message deleted")
        }
        override fun onMemberAdded(member:Member) {
            Log.d(TAG, "Member added: " + member.getIdentity())
        }

        override fun onMemberDeleted(member:Member) {
            Log.d(TAG, "Member deleted: " + member.getIdentity())
        }
        override fun onTypingStarted(member:Member) {
            Log.d(TAG, "Started Typing: " + member.getIdentity())
        }
        override fun onTypingEnded(member:Member) {
            Log.d(TAG, "Ended Typing: " + member.getIdentity())
        }
        override fun onSynchronizationChanged(channel:Channel) {
        }
    }
    internal inner class MessagesAdapter:RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {
        override fun getItemCount(): Int {
            return UserData.Messages.size

        }

        internal inner class ViewHolder(view:View):RecyclerView.ViewHolder(view) {
            var mMessageTextView:TextView
            init{
                mMessageTextView = view.findViewById(R.id.message_text)
            }
        }

        override fun getItemViewType(position: Int): Int {
            val message = UserData.Messages.get(position)
                if (message.author == getString(R.string.username)){
                    return 1
                }

            return 0
        }
        override fun onCreateViewHolder(parent:ViewGroup,
                               viewType:Int):MessagesAdapter.ViewHolder {
            var view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_text_view_stacy, parent, false) as View
            if (viewType == 1)
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_text_view, parent, false) as View
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder:ViewHolder, position:Int) {
            val message = UserData.Messages.get(position)
                Log.d(TAG, message.toString())
                Log.d(TAG, message.messageBody)

                holder.mMessageTextView.setText(message.messageBody)


        }

    }


    companion object {
        // Exposed the server url, uh oh. It's ok it's a hackathon project.
        internal val SERVER_TOKEN_URL = "http://35.202.120.11/mhacks_chad/token"
        internal val DEFAULT_CHANNEL_NAME = "general"
        internal val TAG = "TwilioChat"
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if(android.R.id.home == item?.itemId){
            startActivity(Intent(applicationContext, MainActivity::class.java))
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        startActivity(Intent(applicationContext, MainActivity::class.java))
        finish()
        super.onBackPressed()
    }

}
