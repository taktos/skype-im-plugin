package hudson.plugins.skype.im.transport;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

import com.skype.Chat;
import com.skype.Chat.Status;
import com.skype.SkypeException;

/**
 * 1-on-1 Jabber chat.
 *
 * @author kutzi
 */
public class SkypeChat implements IMChat {

    private final Chat chat;
    private SkypeMessageListenerAdapter messageListener;

    public SkypeChat(Chat chat) {
        this.chat = chat;

    }

    public void sendMessage(String msg) throws IMException {
        try {
            this.chat.send(msg);
        } catch (SkypeException e) {
            throw new IMException(e);
        }
    }

    public String getNickName(String sender) {
        return sender;
    }

    public void addMessageListener(IMMessageListener listener) {
        //this.messageListener = new SkypeMessageListenerAdapter(listener);
        //try {
        //    SkypeImpl.addChatMessageListener(messageListener);
        //} catch (SkypeException ex) {
        //    Logger.getLogger(SkypeChat.class.getName()).log(Level.SEVERE, null, ex);
       // }
    }

    public void removeMessageListener(IMMessageListener listener) {
        // doesn't work out-of the box with Smack

        //SkypeImpl.removeChatMessageListener(messageListener);

    }

    public boolean isMultiUserChat() {
        try {
            return chat.getStatus().equals(Status.MULTI_SUBSCRIBED);
        } catch (SkypeException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getIMId(String user) {
        return user;
    }

    @Override
    public boolean isCommandsAccepted() {
        return true;
    }
}
