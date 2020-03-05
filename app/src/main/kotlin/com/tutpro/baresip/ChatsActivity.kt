package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*

import java.util.*

class ChatsActivity: AppCompatActivity() {

    internal lateinit var uaMessages: ArrayList<Message>
    internal lateinit var listView: ListView
    internal lateinit var clAdapter: ChatListAdapter
    internal lateinit var peerUri: AutoCompleteTextView
    internal lateinit var plusButton: ImageButton

    internal var aor = ""

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        listView = findViewById(R.id.chats) as ListView
        plusButton = findViewById(R.id.plusButton) as ImageButton

        aor = intent.extras!!.getString("aor")!!
        Utils.addActivity("chats,$aor")

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "${getString(R.string.account)} ${aor.split(":")[1]}"
        headerView.text = headerText

        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, uaMessages)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val i = Intent(this, ChatActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", uaMessages[pos].peerUri)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.CHAT_CODE)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", uaMessages[pos].peerUri)
                        i.putExtras(b)
                        startActivityForResult(i, MainActivity.CONTACT_CODE)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        val peerUri = uaMessages[pos].peerUri
                        val msgs = ArrayList<Message>()
                        for (m in Message.messages())
                            if ((m.aor != aor) || (m.peerUri != peerUri))
                                msgs.add(m)
                            else
                                clAdapter.remove(m)
                        clAdapter.notifyDataSetChanged()
                        BaresipService.messages = msgs
                        Message.save()
                        uaMessages = uaMessages(aor)
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }

            val builder = AlertDialog.Builder(this@ChatsActivity, R.style.Theme_AppCompat)
            val peer = ContactsActivity.contactName(uaMessages[pos].peerUri)
            if (peer.startsWith("sip:"))
                builder.setMessage(String.format(getString(R.string.long_chat_question),
                        Utils.friendlyUri(peer, Utils.aorDomain(aor))))
                        .setNeutralButton(getText(R.string.cancel), dialogClickListener)
                        .setNegativeButton(getText(R.string.delete), dialogClickListener)
                        .setPositiveButton(getText(R.string.add_contact), dialogClickListener)
                        .show()
            else
                builder.setMessage(String.format(getString(R.string.short_chat_question), peer))
                        .setNeutralButton(getText(R.string.cancel), dialogClickListener)
                        .setNegativeButton(getText(R.string.delete), dialogClickListener)
                        .show()
            true
        }

        peerUri = findViewById(R.id.peer) as AutoCompleteTextView
        peerUri.threshold = 2
        peerUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map{Contact -> Contact.name}))

        plusButton.setOnClickListener {
            val uriText = peerUri.text.toString().trim()
            if (uriText.length > 0) {
                var uri = ContactsActivity.findContactURI(uriText)
                if (!uri.startsWith("sip:")) {
                    uri = "sip:$uri"
                    if (!uri.contains("@")) {
                        val host = aor.substring(aor.indexOf("@") + 1)
                        uri = "$uri@$host"
                    }
                }
                if (!Utils.checkSipUri(uri)) {
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.invalid_chat_peer_uri))
                } else {
                    peerUri.text.clear()
                    peerUri.isCursorVisible = false
                    val i = Intent(this@ChatsActivity, ChatActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", aor)
                    b.putString("peer", uri)
                    i.putExtras(b)
                    startActivityForResult(i, MainActivity.CHAT_CODE)
                }
            }
        }

    }

    override fun onResume() {

        super.onResume()

        clAdapter.clear()
        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, uaMessages)
        listView.adapter = clAdapter

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.chats_menu, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.delete_chats -> {
                val deleteDialog = AlertDialog.Builder(this@ChatsActivity)
                deleteDialog.setMessage(String.format(getString(R.string.delete_chats_alert),
                        aor.substringAfter(":")))
                deleteDialog.setPositiveButton(getText(R.string.delete)) { dialog, _ ->
                    Message.clear(aor)
                    Message.save()
                    uaMessages.clear()
                    clAdapter.notifyDataSetChanged()
                    Account.findUa(aor)!!.account.unreadMessages = false
                    dialog.dismiss()
                }
                deleteDialog.setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                deleteDialog.create().show()
            }

            android.R.id.home -> {
                BaresipService.activities.removeAt(0)
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }

        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == MainActivity.CHAT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                clAdapter.clear()
                uaMessages = uaMessages(aor)
                clAdapter = ChatListAdapter(this, uaMessages)
                listView.adapter = clAdapter
            }
        }

    }

    override fun onBackPressed() {

        BaresipService.activities.removeAt(0)
        val i = Intent()
        setResult(Activity.RESULT_OK, i)
        finish()

    }

    private fun uaMessages(aor: String) : ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in Message.messages().reversed()) {
            if (m.aor != aor) continue
            var found = false
            for (r in res)
                if (r.peerUri == m.peerUri) {
                    found = true
                    break
                }
            if (!found) res.add(m)
        }
        return res
    }

    companion object {

        fun saveUaMessage(aor: String, time: Long) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages()[i].new = false
                    Message.save()
                    return
                }
        }

        fun deleteUaMessage(aor: String, time: Long) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages().removeAt(i)
                    Message.save()
                    return
                }
        }

    }
}
